/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire

import java.io.{Closeable, File, FileInputStream}
import java.net.{InetAddress, InetSocketAddress, Socket}
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Properties
import java.util.function.Consumer
import javafx.application.{Application, Platform}
import javafx.scene.web.{WebEngine, WebView}
import javafx.scene.{Group, Scene}
import javafx.stage.Stage
import javax.net.ssl._

import com.google.common.collect.Tables
import org.koiroha.bombaysapphire.schema.Tables
import org.slf4j.LoggerFactory
import org.w3c.dom.{Element, Node, NodeList}

import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.slick.driver.PostgresDriver.simple._

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// BotBrowser
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * JavaFX の WebEngine を使用してサインインと特定位置の表示を自動化。
 *
 * @author Takami Torao
 */
class BotBrowser extends Application {
  import org.koiroha.bombaysapphire.BotBrowser._

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

    /** 1ステップの移動距離[km] */
    // def distance = conf("distance").toDouble

    // ウィンドウの生成と表示
    primaryStage.setTitle("Bombay Sapphire")
    val root = new Group()
    val scene = new Scene(root, viewSize._1, viewSize._2)
    val view = new WebView()
    view.setZoom(scale)
    /*
    view.setMinSize(5120, 2880)
    view.setPrefSize(5120, 2880)
    view.setMaxSize(5120, 2880)
    */
    view.setMinSize(viewSize._1, viewSize._2)
    view.setPrefSize(viewSize._1, viewSize._2)
    view.setMaxSize(viewSize._1, viewSize._2)
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
          System.exit(0)
        }
      })
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

  // +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
  // シナリオ
  // +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
  /**
   * サインインが完了すると地点表示ループに入り北西側の緯度経度から南東側の緯度経度に向かって特定の距離ごとに表示
   * して行く。
   */
  private[this] class Scenario(conf:Map[String,String], engine:WebEngine, browser:Closeable) {
    private[this] var signedIn = false

    /** アカウント情報 */
    val account = conf("account")
    val password = conf("password")

    /** 次の地点を表示するまでの待機時間 [秒] */
    private[this] val waitInterval = conf.get("interval").map{ _.toLong }.getOrElse(10 * 60L) * 1000L

    /** 表示対象地点をあらかじめ算出してキュー化 */
    private[this] val positions = locally {
      val exploreMethod:ExploreMethod = conf.get("explore_method") match {
        case Some(key) =>
          val offset = """offset\(([\d\.]+),\s([\d\.]+)\)""".r
          key match {
            case "existing_tile_key" => ByTileKeys
            case "offset" => ByOffset(areaSize._1, areaSize._2)
            case "offset()" => ByOffset(areaSize._1, areaSize._2)
            case offset(lat,lng) => ByOffset(lat.toDouble, lng.toDouble)
            case unknown =>
              logger.error(s"unsupporeted explore method: $unknown")
              throw new IllegalArgumentException(unknown)
          }
        case None =>
          logger.warn(s"explore_method が設定されていません. 全てのエリアを探索します.")
          ByOffset(areaSize._1, areaSize._2)
      }
      val points = exploreMethod.allCenterPositions
      logger.info(f"${exploreMethod.toString} ごとに ${points.size}%,d 地点を探索しています")
      val queue = new mutable.Queue[(Double,Double)]
      queue.enqueue(points:_*)
      queue
    }

    private[this] val maxPositions = positions.size

    def next():Unit = if(engine.getLocation == s"https://${Context.RemoteHost}/intel") {
      if (! signedIn) {
        // Step1: <a>Sign In</a> をクリックする
        engine.getDocument.getElementsByTagName("a").toList.find { n => n.getTextContent == "Sign in"} match {
          case Some(a: Element) =>
            val url = a.getAttribute("href")
            engine.load(url)
            logger.info(s"初期ページ表示完了: NEXT -> $url")
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
      browser.close()
    }

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
        browser.close()
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

}

object BotBrowser {
  private[BotBrowser] val logger = LoggerFactory.getLogger(classOf[BotBrowser])
  def main(args:Array[String]):Unit = Application.launch(classOf[BotBrowser], args:_*)

  /** Intel Map のスクリーンサイズ */
  val mapSize = (5120, 2880)
  /** 縮小率 */
  val scale = 800.0 / mapSize._1
  /** Intel Map 表示領域の物理サイズ (800x450) */
  val viewSize = ((mapSize._1 * scale).toInt, (mapSize._2 * scale).toInt)
  /** z=17 において 100[m]=206[pixel] (Retina), 1kmあたりのピクセル数 */
  val unitKmPixels = 206 * (1000.0 / 100)
  /** 表示領域が示す縦横の距離[km] (実際はマージンがあるが) */
  val areaSize = (mapSize._1 / unitKmPixels, mapSize._2 / unitKmPixels)
  logger.info(f"スクリーンの実際の距離: ${areaSize._1}%.2fkm × ${areaSize._2}%.2fkm")

  /** 地球の外周[km] */
  val earthRound = 40000.0
  /** 1kmあたりの緯度 */
  val latUnit = 360.0 / earthRound
  /** 1kmあたりの経度 */
  def lngUnit(lat:Double):Double = latUnit * math.cos(lat / 360 * 2 * math.Pi)

  implicit class _NodeList(nl:NodeList) {
    def toList:List[Node] = (0 until nl.getLength).map{ nl.item }.toList
  }

  trait ExploreMethod {
    def allCenterPositions:Seq[(Double,Double)]
  }

  case class ByOffset(latKm:Double, lngKm:Double) extends ExploreMethod {
    val latOffset = latUnit * latKm / 2
    def lngOffset(lat:Double) = lngUnit(lat) * lngKm / 2
    def allCenterPositions:Seq[(Double,Double)] = {
      def next(lat:Double, lng:Double):Stream[(Double,Double)] = {
        val n = if(lng + lngOffset(lat) <= Context.Region.east){
          (lat, lng + lngOffset(lat) * 2)
        } else if(lat - latOffset >= Context.Region.south){
          (lat - latOffset * 2, Context.Region.west)
        } else {
          (Double.NaN, Double.NaN)
        }
        (lat, lng) #:: next(n._1, n._2)
      }
      next(Context.Region.north + latOffset, Context.Region.west + lngOffset(Context.Region.north)).takeWhile{ ! _._1.isNaN }
    }.filter{ case (lat, lng) =>
      val lat0 = lat - latOffset
      val lat1 = lng + latOffset
      val lng0 = lat - lngOffset(lat)
      val lng1 = lng + lngOffset(lat)
      Context.Region.overlap(lat0, lat1, lng0, lng1)
    }
    override def toString = f"offset($latKm%.2f[km], $lngKm%.2f[km])"
  }

  /**
   * 現在の DB に保存されている tile_key に基づく移動方法。
   * 領域内の全ての tile_key が取得済みであることを想定している場合に使用できる。
   */
  object ByTileKeys extends ExploreMethod {
    def allCenterPositions:Seq[(Double,Double)] = Context.Database.withSession { implicit session =>
      logger.debug("finding tile_keys")
      Tables.Portals.groupBy{ _.tileKey }
        .map{ case (tileKey, c) => (tileKey, c.map{ _.late6 }.max, c.map{ _.late6 }.min, c.map{ _.lnge6 }.max, c.map{ _.lnge6 }.min) }
        .list
        .map { case (tileKey, lat0, lat1, lng0, lng1) => ((lat1.get+lat0.get)/2/1e6, (lng1.get+lng0.get)/2/1e6) }
    }
    override def toString = "tile_keys"
  }

}
