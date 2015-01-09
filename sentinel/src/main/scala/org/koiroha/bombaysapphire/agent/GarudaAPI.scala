package org.koiroha.bombaysapphire.agent

import java.io.IOException
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.{TimerTask, Timer}

import com.twitter.finagle.builder.ClientBuilder
import com.twitter.finagle.http.RequestBuilder
import org.jboss.netty.buffer.{ChannelBuffer, ChannelBuffers}
import org.jboss.netty.handler.codec.http.{HttpResponseStatus, HttpMethod}
import org.koiroha.bombaysapphire.Batch
import org.koiroha.bombaysapphire.geom.{Polygon, LatLng, Region, Rectangle}
import org.slf4j.LoggerFactory
import scala.concurrent.{Future => SFuture, Promise}
import scala.concurrent.ExecutionContext.Implicits.global

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// GarudaAPI
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * プロキシの取得した JSON データを Garuda と通信する処理。
 * @author Takami Torao
 */
class GarudaAPI(url:URL) {
  import GarudaAPI._

  private[this] val client = ClientBuilder()
    .codec(com.twitter.finagle.http.Http())
    .hosts(s"${url.getHost}:${url.getPort}")
    .hostConnectionLimit(5)
    .build()

  def store(method:String, value:String): Unit = {
    _write(method, value, System.currentTimeMillis())
  }

  /** 指定領域を含む tile key の参照 */
  def tileKeys(rect:Rectangle):SFuture[Seq[(String,Rectangle)]] = {
    val request = RequestBuilder()
      .url(s"$url/tileKeys?r=${rect.north},${rect.east},${rect.south},${rect.west}")
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
      .url(s"$url/administrative_district")
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

  private[this] def _write(method:String, value:String, tm:Long):Unit = scala.concurrent.Future {
    val binary = value.getBytes(StandardCharsets.UTF_8)
    val buffer = ChannelBuffers.copiedBuffer(binary)
    val request = RequestBuilder()
      .url(s"$url/logs/$method")
      .setHeader("Content-Type", "application/json; charset=UTF-8")
      .setHeader("Content-Length", binary.length.toString)
      .setHeader("X-Milliseconds-Before", (System.currentTimeMillis() - tm).toString)
      .build(HttpMethod.POST, Some(buffer))
    client(request).onFailure { ex =>
      logger.error(s"fail to send data, waiting retry...", ex)
      Batch.runAfter(30 * 1000L){ _write(method, value, tm) }
    }
  }

}
object GarudaAPI {
  private[GarudaAPI] val logger = LoggerFactory.getLogger(classOf[GarudaAPI])

  private implicit class _ChannelBuffer(c:ChannelBuffer) {
    def readLatLng:LatLng = LatLng(c.readDouble(), c.readDouble())
    def readString:String = {
      val len = c.readInt()
      val buffer = new Array[Byte](len)
      c.readBytes(buffer)
      new String(buffer, StandardCharsets.UTF_8)
    }
  }
}
