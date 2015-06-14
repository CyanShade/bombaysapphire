/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire.agent.sentinel

import java.io.{Closeable, File}
import java.net.InetSocketAddress
import java.text.DateFormat
import javafx.application.{Application, Platform}
import javafx.event.{ActionEvent, Event, EventHandler}
import javafx.scene.Scene
import javafx.scene.control._
import javafx.scene.layout.BorderPane
import javafx.scene.web.WebEngine
import javafx.stage.Stage
import javax.net.ssl._

import org.koiroha.bombaysapphire.agent.sentinel.ui.{DashboardTab, SessionTab}
import org.koiroha.bombaysapphire.agent.{Config, ParasitizedSSLSocketFactory}
import org.koiroha.bombaysapphire.geom.LatLng
import org.koiroha.bombaysapphire.{Batch, BombaySapphire}
import org.slf4j.LoggerFactory
import org.w3c.dom.{Element, Node, NodeList}

import scala.collection.JavaConversions._
import scala.collection.mutable

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Sentinel
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * JavaFX の WebEngine を使用してサインインと特定位置の表示を自動化。
 *
 * @author Takami Torao
 */
class Sentinel extends Application  {
	import org.koiroha.bombaysapphire.agent.sentinel.Sentinel._

	private[this] var context:Option[Context] = None

	private[this] var proxy:Option[EmbeddedProxy] = None
	private[this] val tileKeys = new mutable.HashSet[String]()

	private[this] var clients = Map[Int,Session]()

	// ==============================================================================================
	// アプリケーションの初期化
	// ==============================================================================================
	/**
	 * 設定を読み込みます。
	 */
	override def init():Unit = {

		// 設定/作業ディレクトリを参照
		val dir = new File(getParameters.getUnnamed.toList match {
			case _dir :: rest => _dir
			case Nil => s"${System.getProperty("user.home")}${File.separator}.sentinel"
		})
		logger.debug(s"initializing sentinel by application directory: $dir")

		val context = new Context(dir)
		this.context = Some(context)
		context.save()

		// プロキシサーバを起動
		val proxy = new EmbeddedProxy {
			private[this] var overLimitCount = 0
			override def store(clientId: Option[Int], method: String, request: String, response: String): Unit = {
				context.destinations.store(method, request, response)
			}
			override def retrieveTileKeys(tileKeys: Set[String]): Unit = Sentinel.this.tileKeys ++= tileKeys
			override def onOverLimit(clientId: Option[Int]): Unit = {
				overLimitCount += 1
				val limit = context.config.overLimitCountToStopScenario
				if(overLimitCount >= limit){
					logger.error(f"アクセス制限に達しました: $overLimitCount%,d/$limit%,d; 哨戒行動を終了します.")
					clientId.foreach{ id => clients.get(id).foreach{ _.signOut() } }
				} else {
					logger.warn(f"アクセス制限を検知しました: $overLimitCount%,d/$limit%,d")
				}
			}
		}
		this.proxy = Some(proxy)

		// WebEngine 向けにかのサイト向けの SSL 通信を全て localhost:443 に向ける
		HttpsURLConnection.setDefaultSSLSocketFactory(
			new ParasitizedSSLSocketFactory(
				BombaySapphire.RemoteHost, 443, new InetSocketAddress("localhost", proxy.httpsPort)))
	}

	// ==============================================================================================
	// アプリケーションの開始
	// ==============================================================================================
	/**
	 * WebView を配置してウィンドウを生成。
	 */
	override def start(primaryStage:Stage):Unit = {

		// メニューバー
		val menuBar = new MenuBar()
		locally {
			val file = new Menu("File")
			val quit = new MenuItem("Quit")
			quit.setOnAction({ e:ActionEvent => this.quit(primaryStage) })
			file.getItems.addAll(quit)
			menuBar.getMenus.addAll(file)
		}

		// クライアント領域
		val clients = new TabPane()
		locally {

			// ダッシュボードタブ
			val dashboard = new DashboardTab()
			clients.getTabs.add(dashboard)

			val tab = new SessionTab(context.get)
			tab.browser.getEngine.load(s"http://${BombaySapphire.RemoteHost}/intel?z=7")
			clients.getTabs.add(tab)
		}

		// 配置
		val root = new BorderPane()
		root.setTop(menuBar)
		root.setCenter(clients)

		// ウィンドウの生成と表示
		primaryStage.setTitle("Sentinel")
		val scene = new Scene(root)
		primaryStage.setScene(scene)
		primaryStage.show()
	}

	override def stop():Unit = {
		// プロキシサーバの停止
		proxy.foreach{ _.close() }
	}

	private[this] def quit(stage:Stage):Unit = {
		stage.close()
	}

	// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	// シナリオ
	// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	/**
	 * サインインが完了すると地点表示ループに入り北西側の緯度経度から南東側の緯度経度に向かって特定の距離ごとに表示
	 * して行く。
	 */
	private[this] class Scenario(conf:Config, engine:WebEngine, browser:Closeable) {
		/** 開始時間 */
		private[this] val start = System.currentTimeMillis()
		private[this] var signedIn = false

		private[this] val waypoints = conf.newWayPoints{ tileKey => tileKeys.contains(tileKey) }
		private[this] val points = waypoints.by(conf.patrolRegion)
		logger.info(f"${waypoints.toString} に対して ${points.remains}%,d 地点を巡回しています")

		private[this] val maxPositions = points.remains
		private[this] var patroledPoints = 0

		def next():Unit = if(engine.getLocation == s"https://${BombaySapphire.RemoteHost}/intel") {
			if (! signedIn) {
				// Step1: <a>Sign In</a> をクリックする
				engine.getDocument.getElementsByTagName("a").toList.find { n => n.getTextContent == "Sign in"} match {
					case Some(a: Element) =>
						val url = a.getAttribute("href")
						engine.load(url)
						logger.info(s"初期ページ表示: $url")
					case _ =>
						logger.error(s"初期ページに Sign-in ボタンが見付かりません")
						browser.close()
				}
			} else {
				// 各地点を表示する
				nextMapMove(5000)
			}
		} else if(engine.getLocation.matches("https://accounts\\.google\\.com/ServiceLogin\\?.*")) {
			// サインインを自動化
			engine.executeScript(
				s"""document.getElementById('Email').value='${conf.account}';
					|document.getElementById('Passwd').value='${conf.password}';
					|document.getElementById('signIn').click();
				""".stripMargin)
			logger.info(s"Sign-in 実行: ${engine.getLocation}")
			signedIn = true
		} else if(engine.getLocation.startsWith(s"https://${BombaySapphire.RemoteHost}/intel?")) {
			None
		} else if(engine.getLocation.startsWith("https://accounts.google.com/CheckCookie?")){
			logger.debug(s"CookieCheck page redirect")
			None
		} else {
			logger.info(s"予期しないページが表示されました: ${engine.getLocation}")
			browser.close()
		}

		// ============================================================================================
		// 表示位置移動ループ
		// ============================================================================================
		/**
		 * 指定時間後に指定された場所を表示。位置が south/east を超えたらウィンドウを閉じる。
		 */
		def nextMapMove(tm:Long):Unit = {
			if(points.remains == 0){
				// 全ての地点を表示し終えていたらウィンドウをクローズ
				logger.debug(s"全ての哨戒が完了しました: $remainsText")
				browser.close()
			} else {
				val LatLng(lat,lng) = points.next()
				def _exec() = {
					patroledPoints += 1
					logger.info(f"[$patroledPoints%,d/${points.remains}%,d] $lat%.6f/$lng%.6f; $remainsText")
					// 指定された位置を表示; z=17 で L0 のポータルが表示されるズームサイズ
					engine.load(s"https://${BombaySapphire.RemoteHost}/intel?ll=$lat,$lng&z=17")
					nextMapMove(conf.intervalSeconds * 1000)
				}
				Batch.runAfter(tm){
					Platform.runLater(new Runnable {
						override def run() = _exec()
					})
				}
			}
		}

		/** ログ出力用の経過時間 */
		private[this] def remainsText:String = {
			def span(msec:Long):String = {
				val sec = msec / 1000
				val t = f"${sec/60/60%24}%d:${sec/60%60}%02d:${sec%60}%02d"
				if(sec < 24 * 60 * 60) t else f"${sec/60/60/24}%,d days, $t"
			}
			val tm = System.currentTimeMillis() - start
			if(points.remains == 0 || patroledPoints == 0){
				span(tm)
			} else {
				val progress = patroledPoints.toDouble / points.remains
				val remains = (tm / progress).toLong
				val assumedEnd = System.currentTimeMillis() + remains
				val df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
				s"${span(tm)} (残り ${span(remains)}; ${df.format(assumedEnd)} 終了予定)"
			}
		}
	}
}

object Sentinel {
	private[Sentinel] val logger = LoggerFactory.getLogger(classOf[Sentinel])
	def main(args:Array[String]):Unit = Application.launch(classOf[Sentinel], args:_*)

	implicit class _NodeList(nl:NodeList) {
		def toList:List[Node] = (0 until nl.getLength).map{ nl.item }.toList
	}

	implicit def _f2EventHandler[T <: Event](f:T=>Unit):EventHandler[T] = new EventHandler[T]{
		override def handle(e:T):Unit = f(e)
	}

}