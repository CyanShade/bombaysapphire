/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire.agent.sentinel

import java.io.{File, FileOutputStream}
import java.net.InetSocketAddress

import com.twitter.finagle.Service
import com.twitter.finagle.builder.{ClientBuilder, ServerBuilder}
import com.twitter.finagle.http.RequestBuilder
import com.twitter.util.Future
import org.jboss.netty.buffer.ChannelBuffer
import org.jboss.netty.handler.codec.http.{HttpMethod, HttpRequest, HttpResponse}
import org.json4s.JObject
import org.koiroha.bombaysapphire.BombaySapphire
import org.slf4j.LoggerFactory

import scala.collection.JavaConversions._
import scala.util.Try

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// EmbeddedProxy
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * ローカルホスト上の利用可能なポート上で Listen し、Sentinel のブラウザリクエストに対してサイト側
 * から返される JSON 情報を取得して Fluentd で送信する HTTP プロキシ。
 *
 * @author Takami Torao
 */
private abstract class EmbeddedProxy {
  import org.koiroha.bombaysapphire.agent.sentinel.EmbeddedProxy._

  /** 接続用の HTTPS クライアント */
  private[this] val Client = ClientBuilder()
    .codec(com.twitter.finagle.http.Http())
    .hosts(s"${BombaySapphire.RemoteHost}:443")
    .tls(BombaySapphire.RemoteHost)
    .hostConnectionLimit(5)
    .build()

  /** プロキシサービス */
  private[this] val ProxyService = new Service[HttpRequest,HttpResponse] {
    private[this] val ua = "(.*) #(\\d+)".r
    def apply(request:HttpRequest):Future[HttpResponse] = {
      val (userAgent, clientId) = request.headers()
        .find{ e => e.getKey.equalsIgnoreCase("user-agent") }
        .map{ e =>
          e.getValue match {
            case ua(u, id) => (u, Some(id.toInt))
            case u => (u, None)
          }
        }.getOrElse((None, None))
      val proxyRequest = request.headers()
        .map{ e => e.getKey -> e.getValue }
        .groupBy{ _._1 }
        .mapValues{ _.toMap.values.toSeq }
        .foldLeft(RequestBuilder()){ case (r, (k, vs)) => r.setHeader(k, vs) }
        .url(s"https://${BombaySapphire.RemoteHost}${request.getUri}")
        .build(request.getMethod, if(request.getMethod == HttpMethod.GET) None else Option(request.getContent))
      proxyRequest.getUri match {
        case UriPrefix(name) =>
          logger.debug(s"${request.getMethod.getName} ${request.getUri}; ${request.getContent.asString}")
          val start = System.currentTimeMillis()
          Client(proxyRequest).filter{ r =>
            val time = System.currentTimeMillis() - start
            logger.debug(f"${r.getStatus.getCode}%s ${r.getStatus.getReasonPhrase}%s (${request.getUri}%s; $time%,d[ms]; ${Option(r.headers().get("Content-Length")).flatMap{l=>Try(l.toLong).toOption}.getOrElse(0L)/1024}%,d[kB])")
            save(clientId, name, proxyRequest, r)
            true
          }
        case _ => Client(proxyRequest)
      }
    }
  }

  private[this] def save(clientId:Option[Int], method:String, request:HttpRequest, response:HttpResponse):Unit = {
    val tm = System.currentTimeMillis()
    val content = response.getContent.asString

    // 正しい JSON 形式であり空で無いことを確認
    org.json4s.native.JsonMethods.parseOpt(content) match {
      case Some(map:JObject) =>
        if(map.values.isEmpty) {
          logger.warn("結果が空です; 呼び出し制限を超えたようです!")
          this.onOverLimit(clientId)
        } else {
          store(clientId, method, request.getContent.asString, content)
          // 取得済みの tile_key を取得する
          if(method == "getEntities"){
            this.retrieveTileKeys(map.values.collect{
              // エラー (TIMEOUT 等) の発生している tile_key を除外
              case (tileKey, map:JObject) if map.values.get("error").isEmpty => tileKey
            }.toSet)
          }
        }
      case Some(unexpected) =>
        logger.warn(s"unexpected result: $content")
      case None =>
        if(response.headers().get("Content-Type").contains("/json")) {
          logger.warn(s"invalid json format: $content")
        } else {
          logger.warn(s"${response.getStatus.getCode} ${response.getStatus.getReasonPhrase}")
        }
    }
  }

  /**
   * ローカルホストから接続可能な HTTP/HTTPS サーバ。
   */
  private[this] val (http, https) = {
    val crt = export("server.crt")
    val key = export("server.key")
    (ServerBuilder()
      .codec(com.twitter.finagle.http.Http())
      .bindTo(new InetSocketAddress("localhost", 0))
      .name("bombay-sapphire")
      .build(ProxyService),
    ServerBuilder()
      .codec(com.twitter.finagle.http.Http())
      .bindTo(new InetSocketAddress("localhost", 0))
      .name("bombay-sapphire-ssl")
      .tls(crt.toString, key.toString)
      .build(ProxyService))
  }
  logger.info(s"bombay-sapphire proxy listening on port http=${http.boundAddress}/https=${https.boundAddress}")

  def httpsPort = https.boundAddress.asInstanceOf[InetSocketAddress].getPort

  def close():Unit = {
    http.close()
    https.close()
  }

  // サーバ証明書をローカルの一時ファイルにコピー
  private[this] def export(name:String):File = {
    import org.koiroha.bombaysapphire.io._
    val file = File.createTempFile("epc", ".$$$")
    file.deleteOnExit()
    using(getClass.getClassLoader.getResourceAsStream(name)){ in =>
      val binary = Iterator.continually(in.read()).takeWhile( _ >= 0).map{ _.toByte }.toArray
      using(new FileOutputStream(file)){ out =>
        out.write(binary)
      }
    }
    file
  }

  /**
   * Intel へのリクエスト/レスポンスを保存する時に呼び出されます。
   * @param clientId クライアントID
   * @param method リクエストメソッド
   * @param request リクエスト内容
   * @param response レスポンス内容
   */
  def store(clientId:Option[Int], method:String, request:String, response:String)
  /** タイルキーを取得した場合 */
  def retrieveTileKeys(tileKeys:Set[String])
  /** アカウントが Intel の呼び出し上限にかかったと思われる場合 */
  def onOverLimit(clientId:Option[Int]):Unit
}

private object EmbeddedProxy {
  private[EmbeddedProxy] val logger = LoggerFactory.getLogger(classOf[EmbeddedProxy])

  private[EmbeddedProxy] val UriPrefix = "/r/(.*)".r

  private implicit class _ChannelBuffer(cb:ChannelBuffer){
    def asString:String = {
      val buffer = cb.toByteBuffer
      val binary = new Array[Byte](buffer.limit())
      buffer.get(binary)
      new String(binary)
    }
  }

}
