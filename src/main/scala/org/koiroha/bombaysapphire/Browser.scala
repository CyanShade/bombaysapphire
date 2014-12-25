package org.koiroha.bombaysapphire

import java.io.File
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javafx.application.Application
import javafx.scene.web.WebView
import javafx.scene.{Group, Scene}
import javafx.stage.Stage
import javafx.util.Callback
import javax.net.ssl.{HttpsURLConnection, SSLContext, TrustManager, X509TrustManager}

import org.slf4j.LoggerFactory

class Browser extends Application {

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

  override def start(primaryStage:Stage):Unit = {
    primaryStage.setTitle("Bombay Sapphire")
    val root = new Group()
    val scene = new Scene(root, 1280, 1024)
    val view = new WebView()
    val engine = view.getEngine
    engine.setUserDataDirectory(new File(".cache"))
   // root.getChildren.add(view)
    primaryStage.setScene(scene)
    primaryStage.show()
    BrowserHelper.init(engine)  // なぜか Callback インターフェースが利用できない
    engine.load(s"https://${ProxyServer.RemoteHost}/intel")
    // Mozilla/5.0 (Windows NT 6.1; Win64; x64) AppleWebKit/537.44 (KHTML, like Gecko) JavaFX/8.0 Safari/537.44
    // Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/39.0.2171.95 Safari/537.36
  }

}

object Browser {
  private[Browser] val logger = LoggerFactory.getLogger(classOf[Browser])
  def main(args:Array[String]):Unit = {
    Application.launch(classOf[Browser], args:_*)
  }
}