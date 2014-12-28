/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire

import java.io.{FileInputStream, File}
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.function.Consumer
import java.util.{Properties, Timer, TimerTask}
import javafx.application.{Application, Platform}
import javafx.scene.web.{WebEngine, WebView}
import javafx.scene.{Group, Scene}
import javafx.stage.Stage
import javax.net.ssl.{HttpsURLConnection, SSLContext, TrustManager, X509TrustManager}

import ch.hsr.geohash.GeoHash
import org.koiroha.bombaysapphire.ParasitizedBrowser.Scenario
import org.koiroha.bombaysapphire.schema.Tables
import org.slf4j.LoggerFactory
import org.w3c.dom.{Element, Node, NodeList}
import scala.collection.JavaConversions._
import scala.slick.driver.PostgresDriver.simple._

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// ParasitizedBrowser
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * JavaFX の WebEngine を使用してサインインと特定位置の表示を自動化。
 *
 * @author Takami Torao
 */
class ParasitizedBrowser extends Application {

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
    HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory)
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
    engine.load(s"https://${ProxyServer.RemoteHost}/intel")
  }

}

object ParasitizedBrowser {
  private[ParasitizedBrowser] val logger = LoggerFactory.getLogger(classOf[ParasitizedBrowser])
  def main(args:Array[String]):Unit = Application.launch(classOf[ParasitizedBrowser], args:_*)

  val timer = new Timer("ParasitizedBrowser", true)

  // +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
  // シナリオ
  // +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
  /**
   * サインインが完了すると地点表示ループに入り北西側の緯度経度から南東側の緯度経度に向かって特定の距離ごとに表示
   * して行く。
   */
  private[ParasitizedBrowser] class Scenario(conf:Map[String,String], engine:WebEngine, view:WebView, primaryStage:Stage) extends DBAccess {
    private[this] var step = 0

    /** アカウント情報 */
    val account = conf("account")
    val password = conf("password")

    def next():Unit = if(engine.getLocation == s"https://${ProxyServer.RemoteHost}/intel") {
      if (step == 0) {
        // Step1: <a>Sign In</a> をクリックする
        engine.getDocument.getElementsByTagName("a").toList.find { n => n.getTextContent == "Sign in"} match {
          case Some(a: Element) =>
            val url = a.getAttribute("href")
            engine.load(url)
            logger.info(s"[Step${step+1}] First Page: $url")
            step += 1
          case _ =>
            logger.error(s"[Step${step+1}] Sign-in button not found on start page")
            primaryStage.close()
        }
      } else {
        // 各地点を表示する
        loop(5000)
      }
    } else if(engine.getLocation.matches("https://accounts\\.google\\.com/ServiceLogin\\?.*")) {
      // サインインを自動化
      engine.executeScript(
        s"""document.getElementById('Email').value='$account';
					|document.getElementById('Passwd').value='$password';
					|document.getElementById('signIn').click();
				""".stripMargin)
      logger.info(s"[Step${step + 1}}] Sign-in: ${engine.getLocation}")
      step += 1
    } else if(engine.getLocation.startsWith(s"https://${ProxyServer.RemoteHost}/intel?")){
      None
    } else {
      logger.info(s"[Step${step+1}] Unexpected Location: ${engine.getLocation}")
      primaryStage.close()
    }

    /** 表示範囲 */
    val north = conf("region.north").toDouble
    val south = conf("region.south").toDouble
    val west = conf("region.west").toDouble
    val east = conf("region.east").toDouble
    /** 地球の外周[km] */
    val earthRound = 40000.0
    /** 1kmあたりの緯度 */
    val latUnit = 360.0 / earthRound
    /** 1kmあたりの経度 */
    def lngUnit(lng:Double):Double = latUnit * math.cos(lng / 360 * 2 * math.Pi)
    /** 1ステップの移動距離[km] */
    val distance = conf("distance").toDouble
    /** 次の地点を表示するまでの待機時間 [ミリ秒] */
    val waitInterval = conf("interval").toLong

    // ============================================================================================
    // 表示位置移動ループ
    // ============================================================================================
    /**
     * 指定時間後に指定された場所を表示。位置が south/east を超えたらウィンドウを閉じる。
     */
    def loop(tm:Long, lat:Double = north, lng:Double = west):Unit = {
      def _exec() = {
        // 位置情報のログ表示
        val location = db.withSession { implicit session =>
          val geohash = GeoHash.withCharacterPrecision(lat, lng, 5).toBase32
          Tables.Geohash.filter{ _.geohash === geohash }
            .firstOption.map{ g => s"${g.city}, ${g.state}, ${g.country}" }.getOrElse("unknown")
        }
        logger.debug(f"[Step${step+1}] stepping next location: ($lat%.6f/$lng%.6f); $location")
        step += 1
        // 指定された位置を表示; z=17 で L0 のポータルが表示されるズームサイズ
        engine.load(s"https://${ProxyServer.RemoteHost}/intel?ll=$lat,$lng&z=17")
        // 次の位置へ移動するか終了ならウィンドウをクローズ
        val nextLng = lng + lngUnit(lat) * distance
        if(nextLng <= east){
          loop(waitInterval, lat, nextLng)
        } else {
          val nextLat = lat - latUnit * distance
          if(nextLat >= south){
            loop(waitInterval, nextLat, west)
          } else {
            logger.debug(f"[Step${step+1}] finish")
            primaryStage.close()
          }
        }
      }
      timer.schedule(new TimerTask {
        override def run() = Platform.runLater(new Runnable {
          override def run() = _exec()
        })
      }, tm)
    }
  }

  implicit class _NodeList(nl:NodeList) {
    def toList:List[Node] = (0 until nl.getLength).map{ nl.item }.toList
  }
}