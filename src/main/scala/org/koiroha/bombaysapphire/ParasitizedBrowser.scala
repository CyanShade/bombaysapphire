/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire

import java.io.{File, FileInputStream}
import java.net.{InetAddress, Socket}
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Properties
import java.util.function.Consumer
import javafx.application.{Application, Platform}
import javafx.scene.web.{WebEngine, WebView}
import javafx.scene.{Group, Scene}
import javafx.stage.Stage
import javax.net.ssl._

import org.koiroha.bombaysapphire.ParasitizedBrowser.Scenario
import org.slf4j.LoggerFactory
import org.w3c.dom.{Element, Node, NodeList}

import scala.collection.JavaConversions._
import scala.collection.mutable

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// ParasitizedBrowser
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * JavaFX の WebEngine を使用してサインインと特定位置の表示を自動化。
 *
 * @author Takami Torao
 */
class ParasitizedBrowser extends Application {
  import ParasitizedBrowser.logger

  locally {
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
        logger.debug(s"createSocket(socket, $hostname, $port, $autoClose)")
        val scs = if(hostname == Context.RemoteHost && port == 443) {
          socket.close()
          new Socket("localhost", 8443)
        } else {
          socket
        }
        root.createSocket(scs, hostname, port, autoClose)
      }
      override def createSocket(hostname:String, port:Int):Socket = {
        logger.debug(s"createSocket($hostname, $port)")
        if(hostname == Context.RemoteHost && port == 443) {
          new Socket("localhost", 8443)
        } else {
          new Socket(hostname, port)
        }
      }
      override def createSocket(hostname:String, port:Int, local:InetAddress, localPort:Int): Socket = ???
      override def createSocket(address:InetAddress, port:Int):Socket = {
        logger.debug(s"createSocket($address, $port)")
        if(address.getHostName == Context.RemoteHost && port == 443) {
          new Socket("localhost", 8443)
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
    val config = locally {
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
    val scene = new Scene(root, 800, 480)
    val view = new WebView()
    val engine = view.getEngine
    engine.setUserDataDirectory(new File(".cache"))
    root.getChildren.add(view)
    primaryStage.setScene(scene)
    // なぜか Callback インターフェースが Scala から利用できないため初期化処理だけ Java で行う
    BrowserHelper.init(engine, new Consumer[WebEngine] {
      val scenario = new Scenario(config, engine, view, primaryStage)
      override def accept(e:WebEngine):Unit = scenario.next()
    })
    primaryStage.show()
    engine.setUserAgent(config.getOrElse("user-agent",
      "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/39.0.2171.95 Safari/537.36"))
    // Mozilla/5.0 (Windows NT 6.1; Win64; x64) AppleWebKit/537.44 (KHTML, like Gecko) JavaFX/8.0 Safari/537.44
    // Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/39.0.2171.95 Safari/537.36

    // 初期ページの表示
    engine.load(s"https://${Context.RemoteHost}/intel")
  }

}

object ParasitizedBrowser {
  private[ParasitizedBrowser] val logger = LoggerFactory.getLogger(classOf[ParasitizedBrowser])
  def main(args:Array[String]):Unit = Application.launch(classOf[ParasitizedBrowser], args:_*)

  // +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
  // シナリオ
  // +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
  /**
   * サインインが完了すると地点表示ループに入り北西側の緯度経度から南東側の緯度経度に向かって特定の距離ごとに表示
   * して行く。
   */
  private[ParasitizedBrowser] class Scenario(conf:Map[String,String], engine:WebEngine, view:WebView, primaryStage:Stage) {
    private[this] var signedIn = false

    /** アカウント情報 */
    val account = conf("account")
    val password = conf("password")

    def next():Unit = if(engine.getLocation == s"https://${Context.RemoteHost}/intel") {
      if (! signedIn) {
        // Step1: <a>Sign In</a> をクリックする
        engine.getDocument.getElementsByTagName("a").toList.find { n => n.getTextContent == "Sign in"} match {
          case Some(a: Element) =>
            val url = a.getAttribute("href")
            engine.load(url)
            logger.info(s"初期ページ表示: $url")
          case _ =>
            logger.error(s"初期ページに Sign-in ボタンが見付かりません")
            primaryStage.close()
        }
      } else {
        // 各地点を表示する
        nextMapMove(5000)
      }
    } else if(engine.getLocation.matches("https://accounts\\.google\\.com/ServiceLogin\\?.*")) {
      // サインインを自動化
      engine.executeScript(
        s"""document.getElementById('Email').value='$account';
					|document.getElementById('Passwd').value='$password';
					|document.getElementById('signIn').click();
				""".stripMargin)
      logger.info(s"Sign-in 実行: ${engine.getLocation}")
      signedIn = true
    } else if(engine.getLocation.startsWith(s"https://${Context.RemoteHost}/intel?")){
      None
    } else {
      logger.info(s"予期しないページが表示されました: ${engine.getLocation}")
      primaryStage.close()
    }

    /** 地球の外周[km] */
    val earthRound = 40000.0
    /** 1kmあたりの緯度 */
    val latUnit = 360.0 / earthRound
    /** 1kmあたりの経度 */
    def lngUnit(lat:Double):Double = latUnit * math.cos(lat / 360 * 2 * math.Pi)
    /** 1ステップの移動距離[km] */
    val distance = conf("distance").toDouble
    /** 次の地点を表示するまでの待機時間 [ミリ秒] */
    val waitInterval = conf("interval").toLong
    /** 表示対象地点をあらかじめ算出してキュー化 */
    val positions = locally {
      val queue = new mutable.Queue[(Double,Double)]()
      def next(lat:Double, lng:Double):Stream[(Double,Double)] = {
        val n = if(lng + lngUnit(lat) * distance <= Context.Region.east){
          (lat, lng + lngUnit(lat) * distance)
        } else if(lat - latUnit * distance >= Context.Region.south){
          (lat - latUnit * distance, Context.Region.west)
        } else {
          (Double.NaN, Double.NaN)
        }
        (lat, lng) #:: next(n._1, n._2)
      }
      val points = next(Context.Region.north, Context.Region.west)
        .takeWhile{ ! _._1.isNaN }.filter{ p => Context.Region.contains(p._1, p._2) }
      logger.info(f"${points.size}%,d 地点を探索しています")
      queue.enqueue(points:_*)
      queue
    }
    val maxPositions = positions.size

    // ============================================================================================
    // 表示位置移動ループ
    // ============================================================================================
    /**
     * 指定時間後に指定された場所を表示。位置が south/east を超えたらウィンドウを閉じる。
     */
    def nextMapMove(tm:Long):Unit = {
      if(positions.isEmpty){
        // 全ての地点を表示し終えていたらウィンドウをクローズ
        logger.debug(f"全ての位置が表示されました")
        primaryStage.close()
      } else {
        val (lat, lng) = positions.dequeue()
        def _exec() = {
          logger.info(f"[${positions.size}%,d/$maxPositions%,d] 位置を表示しています: ($lat%.6f/$lng%.6f)")
          // 指定された位置を表示; z=17 で L0 のポータルが表示されるズームサイズ
          engine.load(s"https://${Context.RemoteHost}/intel?ll=$lat,$lng&z=17")
          nextMapMove(waitInterval)
        }
        Batch.runAfter(tm){
          Platform.runLater(new Runnable {
            override def run() = _exec()
          })
        }
      }
    }
  }

  implicit class _NodeList(nl:NodeList) {
    def toList:List[Node] = (0 until nl.getLength).map{ nl.item }.toList
  }
}