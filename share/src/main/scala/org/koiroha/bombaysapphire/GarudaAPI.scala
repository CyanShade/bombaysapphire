package org.koiroha.bombaysapphire

import java.nio.charset.StandardCharsets

import org.jboss.netty.buffer.ChannelBuffer
import org.jboss.netty.handler.codec.http.HttpRequest
import org.koiroha.bombaysapphire.geom.{LatLng, Rectangle, Region}

import scala.concurrent.Future
import scala.util.Try

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// GarudaAPI
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * プロキシの取得した JSON データを Garuda と通信する処理。
 * @author Takami Torao
 */
trait GarudaAPI{

  /** 指定領域を含む tile key の参照 */
  def tileKeys(rect:Rectangle):Future[Seq[(String,Rectangle)]]

  /** 行政区指定の巡回に対する領域の問い合わせ */
  def administrativeDistricts():Future[Seq[(String,String,String)]]

  /** 行政区指定の巡回に対する領域の問い合わせ */
  def administrativeDistrict(country:String, state:String, city:String):Future[Option[Region]]

  /** データの保存 */
  def store(method:String, request:String, response:String, tm:Long):Unit

}

object GarudaAPI {
  val TimeHeader = "X-Milliseconds-Before"

  implicit class _HttpRequest(req:HttpRequest) {
    def getTimestamp:Long = {
      val diff = Option(req.headers().get(TimeHeader)).flatMap{ t => Try(t.toLong).toOption }.getOrElse(0L)
      System.currentTimeMillis() - diff
    }
  }

  implicit class _ChannelBuffer(c:ChannelBuffer) {
    def readLatLng:LatLng = LatLng(c.readDouble(), c.readDouble())
    def readString:String = {
      val len = c.readInt()
      val buffer = new Array[Byte](len)
      c.readBytes(buffer)
      new String(buffer, StandardCharsets.UTF_8)
    }
    def writeLatLng(ll:LatLng) = {
      c.writeDouble(ll.latitude)
      c.writeDouble(ll.longitude)
    }
    def writeString(str:String):Unit = {
      val binary = str.getBytes(StandardCharsets.UTF_8)
      c.writeInt(binary.length)
      c.writeBytes(binary)
    }
  }

}