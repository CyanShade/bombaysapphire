/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire.agent.sentinel

import java.util.concurrent.ArrayBlockingQueue
import java.util.{Timer, TimerTask}
import javafx.scene.web.{WebEngine, WebView}

import org.koiroha.bombaysapphire.BombaySapphire
import org.slf4j.LoggerFactory
import org.w3c.dom.{Element, Node, NodeList}

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
class Session(val id:Int, browser:WebView, userAgent:String)(implicit context:ExecutionContext){
	import org.koiroha.bombaysapphire.agent.sentinel.Session._
	private[this] val logger = LoggerFactory.getLogger(getClass.getName + ":" + id)

	private[this] val engine = browser.getEngine

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
	 * 現在サインインしているアカウント。
	 */
	private[this] var _account:Option[Account] = None

	/**
	 * 現在実行中のシナリオ。
	 */
	private[this] var _scenario:Option[Scenario] = None

	private[this] var _stop = false
	private[this] var _timerTask:Option[TimerTask] = None

	def account = _account.get

	def scenario = _scenario.get

	// ==============================================================================================
	// サインイン
	// ==============================================================================================
	/**
	 * 指定されたアカウントでサインインを行う。
	 * @param account サインインするアカウント
	 * @return
	 */
	def signIn(account:Account):Future[Unit] = this._account match {
		case None =>
			// Step1: 初期ページ表示
			show(s"https://${BombaySapphire.RemoteHost}/intel").flatMap { _ =>
				// Step2: <a>Sign In</a> をクリックする
				engine.getDocument.getElementsByTagName("a").toList.find { n => n.getTextContent.trim.toLowerCase == "sign in"} match {
					case Some(a: Element) =>
						show(a.getAttribute("href")).flatMap { _ =>
							// Step3: ユーザ名/パスワードを入力してサインインの実行
							expectBrowserCallback{
								engine.executeScript(
									s"""document.getElementById('Email').value='${account.username}';
									|document.getElementById('Passwd').value='${account.password}';
									|document.getElementById('signIn').click();
								""".stripMargin)
							}.map{ _ =>
								// サインイン成功
								this._account = Some(account)
								// ※User-Agent の末尾にIDを付けることで EmbeddedProxy がどのクライアントからのリクエストかを検知する
								//  このIDはIntelへのリクエスト時には削除される
								engine.setUserAgent(s"$userAgent #$id:${account.username}")
								this
							}
						}
					case Some(unknown) => throw new IllegalStateException(s"not a element: $unknown")
					case None =>
						Future.failed(new IllegalStateException("初期ページに SignIn ボタンが存在しません"))
				}
			}
		case Some(a) => Future.failed(new IllegalStateException(s"既に ${a.username} でログインしています"))
	}

	// ==============================================================================================
	// サインアウト
	// ==============================================================================================
	/**
	 * 現在のアカウントからサインアウトする。サインインしていない場合は何も行わずすぐに成功を返す。
	 * @return
	 */
	def signOut():Future[Session] = _account match {
		case Some(a) =>
			// <a>sign out</a> のクリック動作を行う
			engine.getDocument.getElementsByTagName("a").toList.find { n => n.getTextContent.trim.toLowerCase == "sign out"} match {
				case Some(a:Element) => show(a.getAttribute("href")).flatMap{ _ => show("about:blank") }.map{ _ => this }
				case Some(unknown) => throw new IllegalStateException(s"not a element: $unknown")
				case None => Future.failed(new IllegalStateException("ページに Sign Out リンクが存在しません"))
			}
		case None => Future.successful(this)
	}

	// ==============================================================================================
	// シナリオの実行
	// ==============================================================================================
	/**
	 * 指定されたシナリオを実行する。
	 * @return 全てのシナリオが完了すると成功となる Future
	 */
	def start(scenario:Scenario):Future[Session] = {
		_stop = false
		_scenario = Some(scenario)
		val promise = Promise[Session]()
		step(scenario.commands.toIterator, promise)
		promise.future.andThen{
			case _ => _scenario = None
		}
	}

	// ==============================================================================================
	// シナリオの中止
	// ==============================================================================================
	/**
	 * シナリオの中止要求を行う。メソッドはすぐに終了するが、実際の停止は非同期で行われる。
	 */
	def stop():Unit = {
		_timerTask.foreach{ tt =>
			tt.cancel()
			tt.run()
		}
		_stop = true
		_timerTask = None
	}

	// ==============================================================================================
	// シナリオの実行
	// ==============================================================================================
	/**
	 * 指定されたシナリオを実行します。
	 */
	private[this] def step(scenario:Iterator[Command], promise:Promise[Session]):Unit = if(scenario.hasNext){
		if(_stop){
			promise.failure(new IllegalStateException(s"処理が中断されました"))
		} else scenario.next() match {
			case Sleep(tm) =>
				_timerTask = Some(Session.setTimeout(tm) {
					_timerTask = None
					step(scenario, promise)
				})
			case Show(latlng, detail) =>
				expectBrowserCallback {
					val lat = (latlng.latitude * 1e6).toInt
					val lng = (latlng.longitude * 1e6).toInt
					engine.load(s"https://${BombaySapphire.RemoteHost}/intel?${if(detail) "pll" else "ll"}=$lat,$lng&z=17")
				}.onComplete{
					case Success(_) => step(scenario, promise)
					case Failure(ex) => promise.failure(ex)
				}
		}
	} else promise.success(this)

	/*
	var overLimit = 0

	val view = new WebView()
	locally {
		view.setZoom(config.scale)
		view.setMinSize(config.viewSize._1, config.viewSize._2)
		view.setPrefSize(config.viewSize._1, config.viewSize._2)
		view.setMaxSize(config.viewSize._1, config.viewSize._2)
		val engine = view.getEngine

		engine.setUserDataDirectory(new File(s"${System.getProperty("user.home", ".")}/.bombaysapphire/sentinel/cache/$id"))
		// ※User-Agent の末尾にIDを付けることで EmbeddedProxy がどのクライアントからのリクエストかを検知する
		//  このIDはIntelへのリクエスト時には削除される
		engine.setUserAgent(s"${config.userAgent} #$id")
		// 初期ページの表示
		engine.load(s"https://${BombaySapphire.RemoteHost}/intel")
	}

	def close():Unit = {
		view.getEngine.load("about:blank")
	}
	*/

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

}
object Session {
	implicit class _NodeList(nl:NodeList) {
		def toList:List[Node] = (0 until nl.getLength).map{ nl.item }.toList
	}

	/**
	 * シナリオ実行に使用するタイマー。
	 */
	private[Session] lazy val Timer = new Timer("ScenarioTimer", true)

	private[Session] def setTimeout(millis:Long)(f: =>Unit):TimerTask = {
		val task = new TimerTask {
			override def run():Unit = f
		}
		Timer.schedule(task, millis)
		task
	}

}