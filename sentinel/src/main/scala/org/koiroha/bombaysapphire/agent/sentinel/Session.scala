/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire.agent.sentinel

import java.util
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.{ConcurrentModificationException, Timer, TimerTask}
import javafx.beans.property.{SimpleDoubleProperty, SimpleLongProperty}
import javafx.scene.web.{WebEngine, WebView}

import org.koiroha.bombaysapphire.BombaySapphire
import org.koiroha.bombaysapphire.agent.sentinel.xml._
import org.slf4j.LoggerFactory
import org.w3c.dom.Element

import scala.collection.JavaConversions._
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Session
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * 1 つのアカウントと WebView を持ちシナリオを実行するセッション。
 *
 * @author Takami Torao
 */
class Session(context:Context, browser:WebView, waypoints:Seq[WayPoint])(implicit _context:ExecutionContext){

	def this(context:Context, browser:WebView)(implicit _context:ExecutionContext)
		= this(context, browser, context.scenario.allWaypoints(context.config.screenRegion))(_context)

	/**
	 * このセッションを JavaVM ヒープ上で識別するための ID。
	 */
	val id = context.newSessionId

	private[this] val logger = LoggerFactory.getLogger(getClass.getName + ":" + id)

	private[this] val engine = browser.getEngine

	private[this] val signedIn = new AtomicBoolean(false)
	private[this] val stopped = new AtomicBoolean(false)
	private[this] var waiting:Option[TimerTask] = None
	private[this] val promise = Promise[Session]()

	private[this] val DefaultZoomLevel = 0.6

	object progressProperty extends SimpleDoubleProperty(0) {
		private var leastExec:Long = System.currentTimeMillis()
		val task = new TimerTask {
			override def run(): Unit = update()
		}
		def pop():Unit = {
			leastExec = System.currentTimeMillis()
			update()
		}
		def update():Unit = try {
			val diff = math.min(context.scenario.interval.average.toInt * 1000, System.currentTimeMillis() - leastExec)
			val total = points.map{ _.interval }.sum
			val left = playScript.map{ _.interval }.sum - diff
			val current = math.min(1.0, (total - left) / total.toDouble)
			this.set(current)
			this.setValue(current)
			terminationProperty.set(System.currentTimeMillis() + left)
			// logger.debug(f"session progressing ${current * 100}%.1f%%")
		} catch {
			case ex:ConcurrentModificationException => None
		}
	}

	/**
	 * 終了時刻予想のプロパティ。
	 */
	val terminationProperty = new SimpleLongProperty(0)

	/**
	 * 画面遷移の結果を受け取る Promise のキュー。
	 */
	private[this] val queue = new ArrayBlockingQueue[Promise[String]](256)

	// なぜか Callback インターフェースが Scala から利用できないため初期化処理だけ Java で行う
	BrowserHelper.init(engine, new BrowserHelper.Callback(){
		override def success(engine:WebEngine):Unit = Option(queue.poll()).foreach{ _.success(engine.getLocation) }
		override def failure(engine:WebEngine):Unit = Option(queue.poll()).foreach{ _.failure(new IllegalStateException(engine.getLocation)) }
	})

	private sealed abstract class Step(val interval:Long) extends (() => Future[String])
	private object Step {
		case class Point(lat:Double, lng:Double) extends Step(3 * 1000L) {
			def apply() = {
				zoom(context.config.screenScale)
				val url = s"https://${BombaySapphire.RemoteHost}/intel?ll=$lat,$lng&z=17"
				logger.info(s"表示: $url")
				context.save()
				show(url)
			}
		}
		case class Portal(lat:Double, lng:Double) extends Step(1 * 1000L) {
			def apply() = {
				zoom(DefaultZoomLevel)
				val url = s"https://${BombaySapphire.RemoteHost}/intel?pll=$lat,$lng&z=17"
				logger.info(s"表示: $url")
				context.save()
				show(url)
			}
		}
		case class Keyword(kwd:String) extends Step(3 * 1000L) {
			def apply() = {
				zoom(context.config.screenScale)
				logger.info(s"キーワード: $kwd")
				val script = s"""document.getElementById('address').value='${kwd.replaceAll("\n", "\\n").replaceAll("'", "\\'")}';
					|document.getElementById('geocode').submit();
					""".stripMargin
				engine.executeScript(script)
				context.account.increment()
				context.save()
				Future.successful(script)
			}
		}
		case class Sleep(override val interval:Long) extends Step(interval) {
			def apply() = {
				logger.info(f"停止: $interval%,d[msec]")
				val promise = Promise[String]()
				waiting = Some(Session.setTimeout(interval){
					waiting = None
					promise.success("")
				})
				promise.future
			}
		}
		case object SignIn extends Step(10 * 1000L) {
			def apply() = {
				logger.info("サインイン")
				signIn().map{ _ => "" }
			}
		}
		case object SignOut extends Step(3 * 1000L) {
			def apply() = {
				logger.info("サインアウト")
				signOut().map{ x => stop(); x }
			}
		}
		case object Noop extends Step(0L) {
			def apply() = Future.successful("NOOP")
		}
	}

	/**
	 * このセッションの実行で表示する位置またはキーワード。
	 */
	private[this] val points:Seq[Step] = Step.SignIn +: waypoints.map{
		case FixedPoint(lat, lng) => Step.Point(lat, lng)
		case Portal(lat, lng) => Step.Portal(lat, lng)
		case Keyword(kwd) => Step.Keyword(kwd)
		case _:Area =>
			throw new IllegalStateException()
			Step.Noop
	}.flatMap{ case e =>
		Seq(e, Step.Sleep(context.scenario.interval.next() * 1000L))
	} :+ Step.SignOut

	// ==============================================================================================
	// シナリオの作成
	// ==============================================================================================
	/**
	 */
	private[this] val playScript:util.Queue[Step] = new util.LinkedList[Step](points)

	// ==============================================================================================
	// 次のステップを実行
	// ==============================================================================================
	/**
	 * 実行キューから次の処理を取り出して実行する。
	 */
	private[this] def step():Unit = if(! stopped.get()){
		val f = playScript.remove()
		ui.fx{
			progressProperty.pop()
			f.apply().onComplete {
				case Success(_) =>
					if (playScript.size() > 0) {
						step()
					} else {
						stop()
					}
				case Failure(ex) =>
					logger.error("fail to execute session", ex)
					stop()
			}
		}
	}

	// ==============================================================================================
	// サインイン
	// ==============================================================================================
	/**
	 * 指定されたアカウントでサインインを行う。
	 */
	private[this] def signIn():Future[String] = if(signedIn.compareAndSet(false, true)) {
		zoom(DefaultZoomLevel)
		val promise = Promise[String]()

		// ※User-Agent の末尾にIDを付けることで EmbeddedProxy がどのクライアントからのリクエストかを検知する
		//  このIDはIntelへのリクエスト時には削除される
		engine.setUserAgent(s"${context.config.userAgent} #$id:${context.account.username}")

		// サインイン成功
		def succeed() = ui.fx {
			signedIn.set(true)
			Session.setTimeout(1000){
				promise.success("SIGNIN")
			}
		}

		// Step2: <a>Sign In</a> をクリックする
		def step2() = ui.fx {
			val links = engine.getDocument.getElementsByTagName("a").map{ _.asInstanceOf[Element] }
			links.find { _.text.trim.toLowerCase == "sign in"} match {
				case Some(a) =>
					logger.info(s"ステップ2: 初期ページ表示")
					show(a.getAttribute("href")).foreach { _ => step3()}
				case None =>
					links.find {_.text.trim.toLowerCase == "sign out"} match {
						case Some(_) =>
							logger.info(s"既にログインしています")
							succeed()
						case None =>
							logger.error(s"all <a> elements: ${links.map{ _.text }.mkString(",") }")
							engine.getDocument.dump()
							promise.failure(new IllegalStateException("初期ページに SignIn ボタンが存在しません"))
					}
			}
		}

		// Step3: ユーザ名/パスワードを入力してサインインの実行
		def step3() = ui.fx {
			expectBrowserCallback {
				engine.executeScript(
					s"""document.getElementById('Email').value='${context.account.username}';
						|var _nx=document.getElementById('next');
						|if(_nx!==null) _nx.click();
						|setTimeout(function(){
						|  document.getElementById('Passwd').value='${context.account.password}';
						|  document.getElementById('signIn').click();
						|}, 3000);
					""".stripMargin)
			}.foreach { _ => succeed() }
		}

		// Step1: 初期ページ表示
		logger.info(s"ステップ1: 初期ページ表示")
		show(s"https://${BombaySapphire.RemoteHost}/intel").foreach { _ => step2() }
		promise.future
	} else {
		throw new IllegalStateException(s"既にログインしています")
	}

	// ==============================================================================================
	// サインアウト
	// ==============================================================================================
	/**
	 * 現在のアカウントからサインアウトする。サインインしていない場合は何も行わずすぐに成功を返す。
	 * @return
	 */
	private[this] def signOut():Future[String] = if(signedIn.compareAndSet(true, false)) {
		zoom(DefaultZoomLevel)
		// <a>sign out</a> のクリック動作を行う
		Try{ engine.getDocument.getElementsByTagName("a").toSeq }.toOption.getOrElse(Seq()).find { case n:Element => n.text.trim.toLowerCase == "sign out" } match {
			case Some(a: Element) =>
				val promise = Promise[String]()
				show(a.getAttribute("href")).foreach { _ => ui.fx {
					promise.completeWith(show(context.config.defaultUrl))
				} }
				promise.future
			case Some(unknown) => throw new IllegalStateException(s"not a element: $unknown")
			case None => Future.failed(new IllegalStateException("ページに Sign Out リンクが存在しません"))
		}
	} else {
		zoom(1)
		Future.successful("SIGNOUT")
	}

	// ==============================================================================================
	// シナリオの実行
	// ==============================================================================================
	/**
	 * 指定されたシナリオを実行する。
	 * @return 全てのシナリオが完了すると成功となる Future
	 */
	def start():Future[Session] = {
		step()
		Session.Timer.scheduleAtFixedRate(progressProperty.task, 1000, 1000)
		promise.future
	}

	// ==============================================================================================
	// シナリオの中止
	// ==============================================================================================
	/**
	 * シナリオの中止要求を行う。メソッドはすぐに終了するが、実際の停止は非同期で行われる。
	 */
	def stop():Unit = if(stopped.compareAndSet(false, true)){
		waiting.foreach{ _.cancel() }
		waiting = None
		if(signedIn.get){
			signOut()
		}
		progressProperty.update()
		progressProperty.task.cancel()
		promise.success(this)
	}

	// ==============================================================================================
	// URL の表示
	// ==============================================================================================
	/**
	 * 指定された URL を表示します。
	 * @return URL 表示完了の結果を表す Future
	 */
	private[this] def show(url:String):Future[String] = {
		context.account.increment()
		expectBrowserCallback{ engine.load(url) }
	}

	// ==============================================================================================
	// URL の表示
	// ==============================================================================================
	/**
	 * 指定された URL を表示します。
	 * @return URL 表示完了の結果を表す Future
	 */
	private[this] def expectBrowserCallback(f: =>Unit):Future[String] = {
		val promise = Promise[String]()
		queue.put(promise)
		f
		promise.future
	}

	// ==============================================================================================
	// ブラウザズームの設定
	// ==============================================================================================
	/**
	 * ブラウザのズームを設定します。
	 */
	private[this] def zoom(d:Double):Unit = {
		import org.koiroha.bombaysapphire.agent.sentinel.ui._WebView
		browser.physicalZoom(d, context.config.physicalScreen)
	}

}
object Session {

	/**
	 * シナリオ実行に使用するタイマー。
	 */
	lazy val Timer = new Timer("ScenarioTimer", true)

	private[Session] def setTimeout(millis:Long)(f: =>Unit):TimerTask = {
		val task = new TimerTask {
			override def run():Unit = try { f } catch {
				case ex:Throwable => ex.printStackTrace()
			}
		}
		Timer.schedule(task, millis)
		task
	}

}