/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire.agent

import java.net.InetSocketAddress
import java.sql.SQLException

import akka.actor.ActorSystem
import com.twitter.finagle.Service
import com.twitter.finagle.builder.{ClientBuilder, ServerBuilder}
import com.twitter.finagle.http.RequestBuilder
import com.twitter.util.Future
import com.typesafe.config.ConfigFactory
import org.jboss.netty.buffer.ChannelBuffer
import org.jboss.netty.handler.codec.http._
import org.json4s.JObject
import org.koiroha.bombaysapphire.{Context, Entities, ParseTask}
import org.slf4j.LoggerFactory

import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.slick.driver.PostgresDriver.simple._
import scala.slick.jdbc.StaticQuery.interpolation

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// ProxyServer
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * ポート 80/443 上で Listen してサイトから返される JSON 情報を取得し ParserActor に渡す HTTP プロキシ。
 * Unix 系 OS でポート 80/443 を使用するには root 権限で起動する必要があるため、デバッガの利便性のためプロセスを
 * 分離している。
 *
 * @author Takami Torao
 */
class ProxyServer {
  import org.koiroha.bombaysapphire.agent.ProxyServer._

  private[this] val system = ActorSystem("bombaysapphire", ConfigFactory.load("proxy.conf"))
  private[this] val worker = system.actorSelection("akka.tcp://bombaysapphire@localhost:2552/user/parser")

  private[this] val _tileKeys = mutable.HashSet[String]()
  def tileKeys = _tileKeys.toSet

  /** 接続用の HTTP クライアント */
  private[this] val Client = ClientBuilder()
    .codec(com.twitter.finagle.http.Http())
    .hosts(s"${Context.RemoteAddress}:443")
    .tls(Context.RemoteHost)
    .hostConnectionLimit(5)
    .build()

  /** プロキシサービス */
  private[this] val ProxyService = new Service[HttpRequest,HttpResponse] {
    def apply(request:HttpRequest):Future[HttpResponse] = {
      val proxyRequest = request.headers()
        .map{ e => e.getKey -> e.getValue }
        .groupBy{ _._1 }
        .mapValues{ _.toMap.values.toSeq }
        .foldLeft(RequestBuilder()){ case (r, (k, vs)) => r.setHeader(k, vs) }
        .url(s"https://${Context.RemoteAddress}${request.getUri}")
        .setHeader("Host", Context.RemoteHost)
        .build(request.getMethod, if(request.getMethod == HttpMethod.GET) None else Option(request.getContent))
      proxyRequest.getUri match {
        case UriPrefix(name) =>
          logger.debug(s"${request.getMethod.getName} ${request.getUri}; ${request.getContent.asString}")
          val start = System.currentTimeMillis()
          Client(proxyRequest).filter{ r =>
            val time = System.currentTimeMillis() - start
            logger.debug(f"${r.getStatus.getCode}%s ${r.getStatus.getReasonPhrase}%s (${request.getUri}%s; $time%,d[ms])")
            save(name, r.getContent, proxyRequest, r)
            true
          }
        case _ => Client(proxyRequest)
      }
    }
  }

  private[this] def save(method:String, c:ChannelBuffer, request:HttpRequest, response:HttpResponse):Unit = {
    val tm = System.currentTimeMillis()
    val content = c.asString
    val req = s"${request.getMethod.getName} ${request.getUri} ${request.getProtocolVersion.getText}\n" +
      request.headers().map{ e => s"${e.getKey}: ${e.getValue}" }.mkString("\n") +
      "\n" + request.getContent.asString
    val res = s"${response.getProtocolVersion.getText} ${response.getStatus.getCode} ${response.getStatus.getReasonPhrase}\n" +
      response.headers().map{ e => s"${e.getKey}: ${e.getValue}" }.mkString("\n") +
      "\n" + content

    // 正しい JSON 形式であり空で無いことを確認
    org.json4s.native.JsonMethods.parseOpt(content) match {
      case Some(map:JObject) =>
        if(map.values.isEmpty) {
          logger.warn("結果が空です; 呼び出し制限を超えたようです!")
        } else {
          // Akka 経由では大きなデータを受け渡しに支障が出るので共有ストレージ的な場所を経由して受け渡し
          Context.Database.withSession { implicit session =>
            val id = scala.util.Random.nextLong()
            try {
              sqlu"insert into intel.logs(id,method,content,request,response) values($id,$method,$content::jsonb,$req,$res)".first.run
              Some(id)
            } catch {
              case ex: SQLException =>
                logger.error(s"cannot insert json log: $content", ex)
                None
            }
          }.foreach { id =>
            worker ! ParseTask(method, id, tm)
          }
          // このプロキシが取得した Entities の tile_key を保存
          if(method == "getEntities"){
            Entities(map).map{ m =>
              _tileKeys.synchronized {
                _tileKeys ++= m.map.keySet
              }
            }
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

  val (http, https) = {
    (ServerBuilder()
      .codec(com.twitter.finagle.http.Http())
      .bindTo(new InetSocketAddress("localhost", 0))
      .name("bombay-sapphire")
      .build(ProxyService),
    ServerBuilder()
      .codec(com.twitter.finagle.http.Http())
      .bindTo(new InetSocketAddress("localhost", 0))
      .name("bombay-sapphire-ssl")
      .tls("server.crt", "server.key")
      .build(ProxyService))
  }
  logger.info(s"starting bombay-sapphire proxy server on port http=${http.localAddress}/https=${https.localAddress}")

  def close():Unit = {
    http.close()
    https.close()
  }

}
object ProxyServer {
  private[ProxyServer] val logger = LoggerFactory.getLogger(classOf[ProxyServer])
  // BasicConfigurator.configure()

  private[ProxyServer] val UriPrefix = "/r/(.*)".r

  private implicit class _ChannelBuffer(cb:ChannelBuffer){
    def asString:String = {
      val buffer = cb.toByteBuffer
      val binary = new Array[Byte](buffer.limit())
      buffer.get(binary)
      new String(binary)
    }
  }
}
