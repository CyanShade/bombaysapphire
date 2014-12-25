package org.koiroha.bombaysapphire

import java.net.InetSocketAddress

import com.twitter.finagle.Service
import com.twitter.finagle.builder.{ClientBuilder, ServerBuilder}
import com.twitter.finagle.http.RequestBuilder
import com.twitter.util.Future
import org.apache.log4j.BasicConfigurator
import org.jboss.netty.buffer.ChannelBuffer
import org.jboss.netty.handler.codec.http.{HttpMethod, HttpRequest, HttpResponse}
import org.slf4j.LoggerFactory

import scala.collection.JavaConversions._
import scala.util.parsing.json.JSON

object ProxyServer extends App {
  val logger = LoggerFactory.getLogger(this.getClass.getName.dropRight(1))
  BasicConfigurator.configure()

  def dec(s:String):String = s.map{ _ + 3 }.map{ _.toChar }.mkString

  def RemoteHost = dec("ttt+fkdobpp+`lj")
  def RemoteAddress = dec("4/+.1+/16+.5-")
  val Client = ClientBuilder()
    .codec(com.twitter.finagle.http.Http())
    .hosts(s"$RemoteAddress:443")
    .tls(RemoteHost)
    .hostConnectionLimit(5)
    .build()
  val UriPrefix = "/r/(.*)".r

  lazy val service = new Service[HttpRequest,HttpResponse] {
    def apply(request:HttpRequest):Future[HttpResponse] = {
      logger.trace("-------------------------")
      logger.trace(s"${request.getMethod} ${request.getUri} ${request.getProtocolVersion.getText}")
      request.headers().foreach{ e =>
        logger.trace(s"${e.getKey}: ${e.getValue}")
      }
      val proxyRequest = request.headers()
        .map{ e => e.getKey -> e.getValue }
        .groupBy{ _._1 }
        .mapValues{ _.toMap.values.toSeq }
        .foldLeft(RequestBuilder()){ case (r, (k, vs)) =>
          r.setHeader(k, vs)
        }
        .url(s"https://$RemoteAddress${request.getUri}")
        .setHeader("Host", RemoteHost)
        .build(request.getMethod, if(request.getMethod == HttpMethod.GET) None else Option(request.getContent))
      logger.trace("---")
      logger.trace(s"${proxyRequest.getMethod.getName} ${proxyRequest.getUri} ${proxyRequest.getProtocolVersion.getText}")
      proxyRequest.headers().foreach{ e =>
        logger.trace(s"${e.getKey}: ${e.getValue}")
      }
      Client(proxyRequest).filter{ r =>
        logger.trace("---")
        logger.trace(s"${r.getProtocolVersion.getText} ${r.getStatus.getCode} ${r.getStatus.getReasonPhrase}")
        r.headers().foreach{ e =>
          logger.trace(s"${e.getKey}: ${e.getValue}")
        }
        proxyRequest.getUri match {
          case UriPrefix(name) => hook(name, r.getContent)
          case _ => None
        }
        true
      }
    }
  }

  def hook(name:String, c:ChannelBuffer):Unit = {
    val buffer = c.toByteBuffer
    val binary = new Array[Byte](buffer.limit())
    buffer.get(binary)
    val content = new String(binary)
    logger.debug(s"--- $name ---")
    JSON.parseFull(content) match {
	    case Some(_value:Map[_,_]) =>
        // デバッグや分析に不要な大量のゴミ情報を除去
        val value = _value.filter{ case (k,_) => k != "b" && k != "c" }
        (name match {
          case "getGameScore" => GameScore(value)
          case "getRegionScoreDetails" => RegionScoreDetails(value)
          case "getPlexts" => Plext(value)
          case "getEntities" => Entities(value)
          case _ => value.asInstanceOf[Map[String,Any]].get("result")
        }) match {
          case Some(obj) =>
            val str = obj.toString
            logger.debug(s"${if(str.length>1000) str.substring(0,1000) else str}")
          case None =>
            import Implicit._
            logger.warn(value.toJSON)
        }
	    case None =>
		    logger.debug(s"parse error: ${content.substring(0, 1000)}...")
    }
    // result.map.….gameEntities[][2]
  }

  logger.info(s"starting bombay-sapphire on port 80/443")
  ServerBuilder()
    .codec(com.twitter.finagle.http.Http())
    .bindTo(new InetSocketAddress(80))
    .name("bombay-sapphire")
    .build(service)
  ServerBuilder()
    .codec(com.twitter.finagle.http.Http())
    .bindTo(new InetSocketAddress(443))
    .name("bombay-sapphire-ssl")
    .tls("server.crt", "server.key")
    .build(service)
}
