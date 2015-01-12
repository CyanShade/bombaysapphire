package org.koiroha.bombaysapphire.agent

import java.io.IOException
import java.net.URL

import com.twitter.finagle.builder.ClientBuilder
import com.twitter.finagle.http.RequestBuilder
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.handler.codec.http.{HttpMethod, HttpResponseStatus}
import org.koiroha.bombaysapphire.Batch
import org.koiroha.bombaysapphire.geom.{Polygon, Rectangle, Region}
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future => SFuture, Promise}

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// GarudaAPI
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * プロキシの取得した JSON データを Garuda と通信する処理。
 * @author Takami Torao
 */
class GarudaAPI(url:URL) extends org.koiroha.bombaysapphire.GarudaAPI {
  import org.koiroha.bombaysapphire.GarudaAPI._
  import org.koiroha.bombaysapphire.agent.GarudaAPI._

  private[this] val client = ClientBuilder()
    .codec(com.twitter.finagle.http.Http())
    .hosts(s"${url.getHost}:${url.getPort}")
    .hostConnectionLimit(5)
    .build()

  /** 指定領域を含む tile key の参照 */
  def tileKeys(rect:Rectangle):SFuture[Seq[(String,Rectangle)]] = {
    val request = RequestBuilder()
      .url(s"$url/tile_keys?r=${rect.north},${rect.east},${rect.south},${rect.west}")
      .build(HttpMethod.GET, None)
    val promise = Promise[Seq[(String,Rectangle)]]()
    client(request).onSuccess{ response =>
      val content = response.getContent
      val tileKeys = (0 until content.readInt()).map{ i =>
        val tileKey = content.readString
        val north = content.readDouble()
        val east = content.readDouble()
        val south = content.readDouble()
        val west = content.readDouble()
        val rect = Rectangle(north, east, south, west)
        (tileKey, rect)
      }
      promise.success(tileKeys)
    }.onFailure { ex => promise.failure(ex) }
    promise.future
  }

  /** 行政区指定の巡回に対する領域の問い合わせ */
  def administrativeDistricts():SFuture[Seq[(String,String,String)]] = {
    val request = RequestBuilder()
      .url(s"$url/administrative_districts")
      .build(HttpMethod.GET, None)
    val promise = Promise[Seq[(String,String,String)]]()
    client(request).onSuccess{ response =>
      response.getStatus match {
        case HttpResponseStatus.OK =>
          val content = response.getContent
          val districts = (0 until content.readInt()).map{ _ =>
            (content.readString, content.readString, content.readString)
          }
          promise.success(districts)
        case unexpected =>
          logger.error(s"unexpected response: $unexpected")
          promise.failure(new IOException(unexpected.getCode + " " + unexpected.getReasonPhrase))
      }
    }.onFailure { ex => promise.failure(ex) }
    promise.future
  }

  /** 行政区指定の巡回に対する領域の問い合わせ */
  def administrativeDistrict(country:String, state:String, city:String):SFuture[Option[Region]] = {
    val request = RequestBuilder()
      .url(s"$url/administrative_district?c=$country&s=$state&ct=$city")
      .build(HttpMethod.GET, None)
    val promise = Promise[Option[Region]]()
    client(request).onSuccess{ response =>
      response.getStatus match {
        case HttpResponseStatus.OK =>
          val content = response.getContent
          val region = Region(content.readString, (0 until content.readInt()).map{ _ =>
            Polygon((0 until content.readInt()).map{ _ => content.readLatLng })
          })
          promise.success(Some(region))
        case HttpResponseStatus.NOT_FOUND =>
          promise.success(None)
        case unexpected =>
          logger.error(s"unexpected response: $unexpected")
          promise.failure(new IOException(unexpected.getCode + " " + unexpected.getReasonPhrase))
      }
    }.onFailure { ex => promise.failure(ex) }
    promise.future
  }

  /** 哨戒情報の保存 */
  def store(method:String, request:String, response:String, tm:Long): Unit = _write(method, request, response, tm)

  private[this] def _write(method:String, req:String, res:String, tm:Long):Unit = scala.concurrent.Future {
    val content = ChannelBuffers.dynamicBuffer()
    content.writeString(req)
    content.writeString(res)
    val request = RequestBuilder()
      .url(s"$url/logs/$method")
      .setHeader("Content-Type", "application/octet-stream")
      .setHeader(TimeHeader, (System.currentTimeMillis() - tm).toString)
      .build(HttpMethod.POST, Some(content))
    client(request).onSuccess{ r =>
      if(r.getStatus.getCode >= 500){
        logger.warn(s"fail to send data, waiting retry...: ${r.getStatus.getCode} ${r.getStatus.getReasonPhrase}")
        Batch.runAfter(30 * 1000L){ _write(method, req, res, tm) }
      } else if(r.getStatus.getCode >= 400){
        logger.error(s"fail to send data: ${r.getStatus.getCode} ${r.getStatus.getReasonPhrase}")
      }
    }.onFailure { ex =>
      logger.warn(s"fail to send data, waiting retry...", ex)
      Batch.runAfter(30 * 1000L){ _write(method, req, res, tm) }
    }
  }

}
object GarudaAPI {
  private[GarudaAPI] val logger = LoggerFactory.getLogger(classOf[GarudaAPI])
}
