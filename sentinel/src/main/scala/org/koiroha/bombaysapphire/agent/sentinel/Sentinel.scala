/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire.agent.sentinel

import java.io.File
import java.net.InetSocketAddress
import javafx.application.Application
import javafx.scene.Scene
import javafx.stage.{WindowEvent, Stage}
import javax.net.ssl._

import org.koiroha.bombaysapphire.BombaySapphire
import org.koiroha.bombaysapphire.agent.ParasitizedSSLSocketFactory
import org.koiroha.bombaysapphire.agent.sentinel.ui._
import org.slf4j.LoggerFactory
import org.w3c.dom.{Node, NodeList}

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

		// 設定ファイルを参照
		val file = new File(getParameters.getUnnamed.toList match {
			case f :: rest => f
			case Nil => s"${System.getProperty("user.home")}${File.separator}sentinel.xml"
		})
		logger.debug(s"initializing sentinel by application conf: $file")

		// コンテキストを読み出し
		val context = new Context(file)
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
					clientId.foreach{ id => clients.get(id).foreach{ _.stop() } }
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
		primaryStage.setOnCloseRequest({ e:WindowEvent =>
			stop()
		})

		// クライアント領域
		val scenario = new ScenarioUI(context.get, primaryStage)

		// ウィンドウの生成と表示
		primaryStage.setTitle("Sentinel")
		val scene = new Scene(scenario)
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

}

object Sentinel {
	private[Sentinel] val logger = LoggerFactory.getLogger(classOf[Sentinel])
	def main(args:Array[String]):Unit = Application.launch(classOf[Sentinel], args:_*)

	implicit class _NodeList(nl:NodeList) {
		def toList:List[Node] = (0 until nl.getLength).map{ nl.item }.toList
	}

}