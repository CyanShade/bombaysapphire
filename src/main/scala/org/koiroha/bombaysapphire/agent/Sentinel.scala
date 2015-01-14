/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire.agent

import java.io.{Closeable, File, FileInputStream}
import java.net.{InetAddress, InetSocketAddress, Socket}
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Properties
import java.util.function.Consumer
import javafx.application.Application
import javafx.scene.web.{WebEngine, WebView}
import javafx.scene.{Group, Scene}
import javafx.stage.Stage
import javax.net.ssl._

import org.koiroha.bombaysapphire.util.Size
import org.koiroha.bombaysapphire.{BrowserHelper, Context}
import org.slf4j.LoggerFactory

import scala.collection.JavaConversions._

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Sentinel
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * JavaFX の WebEngine を使用してサインインと特定位置の表示を自動化。
 *
 * @author Takami Torao
 */
class Sentinel extends Application {
  import org.koiroha.bombaysapphire.agent.Sentinel._

  // プロキシサーバを起動
  private val proxy = new ProxyServer()

  locally {
    val (proxyAddress, proxyPort) = proxy.https.localAddress match {
      case addr:InetSocketAddress => (addr.getAddress, addr.getPort)
      case unexpected => throw new IllegalStateException(unexpected.toString)
    }

    // 自己署名証明書を含む全てのサーバ証明書を無検証で信頼
    val trustAllCerts:Array[TrustManager] = Array(
      new X509TrustManager {
        override def getAcceptedIssuers:Array[X509Certificate] = null
        override def checkClientTrusted(certs:Array[X509Certificate], s:String):Unit = None
        override def checkServerTrusted(certs:Array[X509Certificate], s:String):Unit = None
      }
    )
    val sc = SSLContext.getInstance("SSL")
    sc.init(null, trustAllCerts, new SecureRandom())
    // かのサイト向けの SSL 通信を全て localhost:443 に向ける
    val sf = new SSLSocketFactory {
      val root = sc.getSocketFactory
      override def getDefaultCipherSuites:Array[String] = root.getDefaultCipherSuites
      override def getSupportedCipherSuites:Array[String] = root.getSupportedCipherSuites
      override def createSocket(socket:Socket, hostname:String, port:Int, autoClose:Boolean):Socket = {
        val scs = if(hostname == Context.RemoteHost && port == 443) {
          logger.debug(s"createSocket(socket, $hostname, $port, $autoClose) forward to ${proxyAddress.getHostName}:$proxyPort")
          socket.close()
          new Socket(proxyAddress, proxyPort)
        } else {
          socket
        }
        root.createSocket(scs, hostname, port, autoClose)
      }
      override def createSocket(hostname:String, port:Int):Socket = {
        if(hostname == Context.RemoteHost && port == 443) {
          logger.debug(s"createSocket($hostname, $port)")
          new Socket(proxyAddress, proxyPort)
        } else {
          new Socket(hostname, port)
        }
      }
      override def createSocket(hostname:String, port:Int, local:InetAddress, localPort:Int): Socket = ???
      override def createSocket(address:InetAddress, port:Int):Socket = {
        if(address.getHostName == Context.RemoteHost && port == 443) {
          logger.debug(s"createSocket($address, $port)")
          new Socket(proxyAddress, proxyPort)
        } else {
          new Socket(address, port)
        }
      }
      override def createSocket(address:InetAddress, port:Int, local:InetAddress, localPort:Int): Socket = ???
    }
    HttpsURLConnection.setDefaultSSLSocketFactory(sf)

  }

  // ==============================================================================================
  // アプリケーションの開始
  // ==============================================================================================
  /**
   * WebView を配置してウィンドウを生成。
   */
  override def start(primaryStage:Stage):Unit = {
    // 設定ファイルの参照
    val conf = locally {
      val file = getParameters.getNamed.getOrDefault("config", "conf/bot.properties")
      val prop = new Properties()
      val in = new FileInputStream(file)
      prop.load(in)
      in.close()
      prop.map{ case (k,v) => k.toString -> v.toString }.toMap
    }

    // ウィンドウの生成と表示
    primaryStage.setTitle("Bombay Sapphire")
    val root = new Group()
    val scene = new Scene(root, viewSize.width, viewSize.height)
    val view = new WebView()
    view.setZoom(scale)
    view.setMinSize(viewSize.width, viewSize.height)
    view.setPrefSize(viewSize.width, viewSize.height)
    view.setMaxSize(viewSize.width, viewSize.height)
    val engine = view.getEngine
    engine.setUserDataDirectory(new File(".cache"))
    root.getChildren.add(view)
    primaryStage.setScene(scene)
    // なぜか Callback インターフェースが Scala から利用できないため初期化処理だけ Java で行う
    BrowserHelper.init(engine, new Consumer[WebEngine] {
      val scenario = new Scenario(conf, engine, new Closeable() {
        val start = System.currentTimeMillis()
        override def close(): Unit = {
          val t = System.currentTimeMillis() - start
          logger.info(f"${t/60/1000}%,d分${t/1000}%d秒でクロールを終了しました")
          primaryStage.close()
          proxy.close()
        }
      }, proxy)
      override def accept(e:WebEngine):Unit = scenario.next()
    })
    primaryStage.show()
    engine.setUserAgent(conf.getOrElse("user-agent",
      "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/39.0.2171.95 Safari/537.36"))
    // Mozilla/5.0 (Windows NT 6.1; Win64; x64) AppleWebKit/537.44 (KHTML, like Gecko) JavaFX/8.0 Safari/537.44
    // Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/39.0.2171.95 Safari/537.36

    // 初期ページの表示
    engine.load(s"https://${Context.RemoteHost}/intel")
  }

}

object Sentinel {
  private[Sentinel] val logger = LoggerFactory.getLogger(classOf[Sentinel])
  def main(args:Array[String]):Unit = Application.launch(classOf[Sentinel], args:_*)

  /** Intel Map のスクリーンサイズ */
  val mapSize = Size(5120, 2880)
  /** 縮小率 */
  val scale = 800.0 / mapSize.width
  /** Intel Map 表示領域の物理サイズ (800x450) */
  val viewSize = Size((mapSize.width * scale).toInt, (mapSize.height * scale).toInt)
  /** z=17 において 100[m]=206[pixel] (Retina), 1kmあたりのピクセル数 */
  val unitKmPixels = 206 * (1000.0 / 100)
  /** 表示領域が示す縦横の距離[km] (実際はマージンがあるが) */
  val areaSize = (mapSize.width / unitKmPixels, mapSize.height / unitKmPixels)
  logger.info(f"スクリーンの実際の距離: ${areaSize._1}%.2fkm × ${areaSize._2}%.2fkm")

}