/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire.garuda

import java.net.{InetSocketAddress, URLDecoder}
import java.nio.charset.StandardCharsets
import java.util.Date

import com.twitter.finagle.Service
import com.twitter.finagle.builder.ServerBuilder
import com.twitter.finagle.http.Http
import com.twitter.util.{StorageUnit, Future, Promise}
import org.jboss.netty.buffer.{ChannelBuffer, ChannelBuffers}
import org.jboss.netty.handler.codec.http._
import org.json4s.DefaultFormats
import org.koiroha.bombaysapphire.geom.{Polygon, Rectangle}
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Server
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * ProxyServer から渡された JSON を解析しそれぞれのエンティティごとに格納する。
 * @author Takami Torao
 */
class Server(context:Context, address:String, port:Int) {
	import org.koiroha.bombaysapphire.GarudaAPI._
	private[Server] val logger = LoggerFactory.getLogger(classOf[Server])
	private[this] implicit val formats = DefaultFormats

	private[this] var server:Option[com.twitter.finagle.builder.Server] = None

	def start():Unit = {
		server = Some(ServerBuilder()
			.codec(Http(_maxRequestSize = new StorageUnit(50 * 1024 * 1024)))
			.bindTo(new InetSocketAddress(address, port))
			.name("garuda")
			.build(service))
		logger.info(s"Garuda API サーバを起動しました: $address:$port")
	}

	def stop():Unit = {
		server.foreach{ _.close() }
		server = None
		logger.info(s"Garuda API サーバを停止しました")
	}

	/**
	 * Sentinel の送信するデータを受信したり、Sentinel に情報を渡すための HTTP サーバ。
	 */
	private[this] val service = new Service[HttpRequest,HttpResponse] {
		val prefix = "/api/1\\.0"
		val store = s"$prefix/logs/([a-zA-Z0-9]+)".r
		val tileKeys = s"$prefix/tile_keys\\?r=(.+)".r
		val administrativeDistricts = s"$prefix/administrative_districts".r
		val administrativeDistrict = s"$prefix/administrative_district\\?(.*)".r
		def apply(request:HttpRequest):Future[HttpResponse] = {
			request.getUri match {
				case store(method) =>
					val tm = request.getTimestamp
					val content = request.getContent
					val proxyRequest = content.readString
					val proxyResponse = content.readString
					context.garuda.store(method, proxyRequest, proxyResponse, tm)
					success()
				case tileKeys(query) =>
					val array = query.split("\\s*,\\s*")
					array.find{ d => Try(d.toDouble).isFailure } match {
						case Some(value) =>
							badRequest(s"malformed double value: '$value'")
						case None =>
							array.map {_.toDouble} match {
								case Array(north, east, south, west) =>
									val promise = Promise[HttpResponse]()
									context.garuda.tileKeys(Rectangle(north, east, south, west)).map{ tileKeys =>
										val content = ChannelBuffers.dynamicBuffer()
										content.writeInt(tileKeys.size)
										tileKeys.foreach { tk =>
											content.writeString(tk._1)
											content.writeDouble(north)
											content.writeDouble(east)
											content.writeDouble(south)
											content.writeDouble(west)
										}
										response(200, content, -1, "application/octet-stream", cache = false)
									}.onComplete{
										case Success(response) => promise.setValue(response)
										case Failure(ex) => promise.setException(ex)
									}
									promise
								case _ =>
									badRequest(s"malformed rectangle values: '$query'")
							}
					}
				case administrativeDistricts() =>
					val promise = Promise[HttpResponse]()
					context.garuda.administrativeDistricts().map{ dists =>
						val content = ChannelBuffers.dynamicBuffer()
						content.writeInt(dists.size)
						dists.foreach { d =>
							content.writeString(d._1)
							content.writeString(d._2)
							content.writeString(d._3)
						}
						response(200, content, -1, "application/octet-stream", cache = false)
					}.onComplete{
						case Success(response) => promise.setValue(response)
						case Failure(ex) => promise.setException(ex)
					}
					promise
				case administrativeDistrict(q) =>
					val query = parseQuery(q)
					val country = query.getOrElse("c", "")
					val state = query.getOrElse("s", "")
					val city = query.getOrElse("ct", "")
					val promise = Promise[HttpResponse]()
					context.garuda.administrativeDistrict(country, state, city).map{
						case Some(region) =>
							val content = ChannelBuffers.dynamicBuffer()
							content.writeString(region.name)
							content.writeInt(region.shapes.size)
							region.shapes.foreach{
								case poly:Polygon =>
									content.writeInt(poly.points.size)
									poly.points.foreach{ p =>
										content.writeLatLng(p)
									}
								case unknown =>
									logger.error(s"unexpected shape: $unknown")
									content.writeInt(0)
							}
							response(200, content, -1, "application/octet-stream", cache = false)
						case None =>
							response(404, s"not found: $q", "text/plain", cache = false)
					}.onComplete{
						case Success(response) => promise.setValue(response)
						case Failure(ex) => promise.setException(ex)
					}
					promise
			}
		}
	}

	private[this] def parseQuery(query:String):Map[String,String] = {
		query.split("&").map{ _.split("=", 2).map{ s => URLDecoder.decode(s,"UTF-8")} }.map{
			case Array(key, value) => key -> value
			case Array(key) => key -> ""
		}.toMap
	}

	private[this] def success(text:String = "ok") = Future.value(response(200, "ok", "text/plain", cache = false))

	private[this] def badRequest(text:String) = Future.value(response(400, text, "text/plain", cache = false))

	private[this] def response(code:Int, text:String, contentType:String, cache:Boolean):HttpResponse = {
		response(code, text.getBytes(StandardCharsets.UTF_8), contentType + ";charset=UTF-8", cache)
	}

	private[this] def response(code:Int, binary:Array[Byte], contentType:String, cache:Boolean):HttpResponse = {
		response(code, ChannelBuffers.copiedBuffer(binary), binary.length, contentType, cache)
	}

	private[this] def response(code:Int, content:ChannelBuffer, length:Int, contentType:String, cache:Boolean):HttpResponse = {
		val response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.valueOf(code))
		response.headers.set("Server", "Garuda/1.0")
		response.headers.set("Date", new Date())
		response.headers.add("Content-Type", contentType)
		if(length >= 0){
			response.headers.add("Content-Length", length)
		}
		if(! cache){
			response.headers.add("Cache-Control", "no-cache")
		}
		response.setContent(content)
		response
	}

}
object Server {
	private[Server] val logger = LoggerFactory.getLogger(classOf[Server])
}
