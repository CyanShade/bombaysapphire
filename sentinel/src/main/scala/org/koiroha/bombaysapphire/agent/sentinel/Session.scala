/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire.agent.sentinel

import java.util
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.{Timer, TimerTask}
import javafx.application.Platform
import javafx.scene.web.{WebEngine, WebView}

import org.koiroha.bombaysapphire.BombaySapphire
import org.koiroha.bombaysapphire.agent.sentinel.xml._
import org.slf4j.LoggerFactory
import org.w3c.dom.Element

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Session
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * 1 つのアカウントと WebView を持ちシナリオを実行するセッション。
 *
 * @author Takami Torao
 */
class Session(val id:Int, context:Context, scenario:Scenario, browser:WebView, userAgent:String)(implicit _context:ExecutionContext){
	private[this] val logger = LoggerFactory.getLogger(getClass.getName + ":" + id)

	private[this] val engine = browser.getEngine

	private[this] val signedIn = new AtomicBoolean(false)
	private[this] val stopped = new AtomicBoolean(false)
	private[this] var waiting:Option[TimerTask] = None
	private[this] val promise = Promise[Session]()

	private[this] val DefaultZoomLevel = browser.getZoom

	/**
	 * 画面遷移の結果を受け取る Promise のキュー。
	 */
	private[this] val queue = new ArrayBlockingQueue[Promise[String]](256)

	// なぜか Callback インターフェースが Scala から利用できないため初期化処理だけ Java で行う
	BrowserHelper.init(engine, new BrowserHelper.Callback(){
		override def success(engine:WebEngine):Unit = Option(queue.poll()).foreach{ _.success(engine.getLocation) }
		override def failure(engine:WebEngine):Unit = Option(queue.poll()).foreach{ _.failure(new IllegalStateException(engine.getLocation)) }
	})

	/**
	 * このセッションの実行で表示する位置またはキーワード。
	 */
	val points = scenario.waypoints.flatMap {
		case area: Area => area.toWayPoints(context.config.distanceKM)
		case wp: WayPoint => Seq(wp)
	}

	// ==============================================================================================
	// シナリオの作成
	// ==============================================================================================
	/**
	 */
	private[this] val playScript:util.Queue[()=>Future[String]] = {
		def _point(lat:Double, lng:Double):()=>Future[String] = {
			{ () =>
				browser.setZoom(context.config.screenScale)
				val url = s"https://${BombaySapphire.RemoteHost}/intel?ll=$lat,$lng&z=7"
				logger.info(s"表示: $url")
				show(url)
			}
		}
		def _portal(lat:Double, lng:Double):()=>Future[String] = {
			{ () =>
				browser.setZoom(DefaultZoomLevel)
				val url = s"https://${BombaySapphire.RemoteHost}/intel?pll=$lat,$lng&z=7"
				logger.info(s"表示: $url")
				show(url)
			}
		}
		def _keyword(kwd:String):()=>Future[String] = {
			{ () =>
				browser.setZoom(context.config.screenScale)
				logger.info(s"キーワード: $kwd")
				val script = s"""document.getElementById('address').value='${kwd.replaceAll("\n", "\\n").replaceAll("'", "\\'")}';
					|document.getElementById('geocode').submit();
					""".stripMargin
				engine.executeScript(script)
				Future.successful(script)
			}
		}
		def _sleep(interval:Long):()=>Future[String] = {
			{ () =>
				logger.info(f"停止: $interval%,d[msec]")
				val promise = Promise[String]()
				waiting = Some(Session.setTimeout(interval){
					waiting = None
					promise.success("")
				})
				promise.future
			}
		}

		// 領域型のオブジェクトを表示位置に展開
		val execs = points.map{
			case FixedPoint(lat, lng) => _point(lat, lng)
			case Portal(lat, lng) => _portal(lat, lng)
			case Keyword(keyword) => _keyword(keyword)
			case x => throw new IllegalStateException(s"unknown object: $x")
		}

		val queue = new util.LinkedList[()=>Future[String]]()
		queue.add({ () =>
			logger.info("サインイン")
			signIn().map{ _ => "" }
		})
		execs.foreach{ case e =>
			queue.add(e)
			val interval = scenario.interval.next() * 1000L
			queue.add(_sleep(interval))
		}
		queue.add({ () =>
			logger.info("サインアウト")
			signOut().map{ x => stop(); x }
		})
		queue
	}

	private[this] def step():Unit = if(! stopped.get()){
		val f = playScript.remove()
		edt{
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
		browser.setZoom(DefaultZoomLevel)
		val account:Account = context.accounts.list.find{ _.username == scenario.account }.get
		val promise = Promise[String]()

		// ※User-Agent の末尾にIDを付けることで EmbeddedProxy がどのクライアントからのリクエストかを検知する
		//  このIDはIntelへのリクエスト時には削除される
		engine.setUserAgent(s"$userAgent #$id:${account.username}")

		// Step2: <a>Sign In</a> をクリックする
		def step2() = edt {
			val links = engine.getDocument.getElementsByTagName("a").map{ _.asInstanceOf[Element] }
			links.find { _.text.trim.toLowerCase == "sign in"} match {
				case Some(a) =>
					show(a.getAttribute("href")).foreach { _ => step3() }
				case None =>
					logger.error(s"all <a> elements: ${links.map{ _.text }.mkString(",") }")
					engine.getDocument.dump()
					promise.failure(new IllegalStateException("初期ページに SignIn ボタンが存在しません"))
			}
		}

		// Step3: ユーザ名/パスワードを入力してサインインの実行
		def step3() = edt {
			expectBrowserCallback {
				engine.executeScript(
					s"""document.getElementById('Email').value='${account.username}';
									|document.getElementById('Passwd').value='${account.password}';
									|document.getElementById('signIn').click();
								""".stripMargin)
			}.foreach { _ =>
				// サインイン成功
				signedIn.set(true)
				promise.success("SIGNIN")
			}
		}

		// Step1: 初期ページ表示
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
		browser.setZoom(DefaultZoomLevel)
		// <a>sign out</a> のクリック動作を行う
		engine.getDocument.getElementsByTagName("a").toSeq.find { n => n.getTextContent.trim.toLowerCase == "sign out"} match {
			case Some(a: Element) =>
				val promise = Promise[String]()
				show(a.getAttribute("href")).foreach { _ => edt {
					promise.completeWith(show("about:blank"))
				} }
				promise.future
			case Some(unknown) => throw new IllegalStateException(s"not a element: $unknown")
			case None => Future.failed(new IllegalStateException("ページに Sign Out リンクが存在しません"))
		}
	} else {
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
		if(signedIn.get){
			signOut()
		}
		promise.success(this)
	}

	// ==============================================================================================
	// URL の表示
	// ==============================================================================================
	/**
	 * 指定された URL を表示します。
	 * @return URL 表示完了の結果を表す Future
	 */
	private[this] def show(url:String):Future[String] = expectBrowserCallback{ engine.load(url) }

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

	private[this] def edt[U](f: =>U):Future[U] = if(Platform.isFxApplicationThread){
		Future.successful(f)
	} else {
		val promise = Promise[U]()
		Platform.runLater(new Runnable {
			override def run() = {
				promise.success(f)
			}
		})
		promise.future
	}

}
object Session {


	/**
	 * シナリオ実行に使用するタイマー。
	 */
	private[this] lazy val Timer = new Timer("ScenarioTimer", true)

	private[Session] def setTimeout(millis:Long)(f: =>Unit):TimerTask = {
		val task = new TimerTask {
			override def run():Unit = f
		}
		Timer.schedule(task, millis)
		task
	}

}