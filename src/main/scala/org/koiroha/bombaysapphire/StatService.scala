/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire

import java.net.{URI, URLDecoder}
import java.sql.Timestamp
import java.text.{DateFormat, SimpleDateFormat}

import com.twitter.finagle.Service
import com.twitter.util.Future
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.handler.codec.http._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._
import org.slf4j.LoggerFactory

import scala.slick.driver.PostgresDriver.simple._
import scala.util.Try

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// StatService
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * ProxyServer からバイパスして特定の URL に対して統計情報を出力。
 * @author Takami Torao
 */
class StatService extends Service[HttpRequest,HttpResponse] {
	import org.koiroha.bombaysapphire.schema.Tables._

	private[this] val logger = LoggerFactory.getLogger(classOf[StatService])

	def apply(request:HttpRequest):Future[HttpResponse] = try {
		val uri = URI.create(request.getUri)
		val param = Option(uri.getQuery) match {
			case Some("") => Map[String,String]()
			case None => Map[String,String]()
			case Some(query) =>
				query.split("&").map {_.split("=")}.map{ case Array(k,v) => k->v; case Array(k) => k->""}
					.map { case (k, v) => URLDecoder.decode(k, "UTF-8") -> URLDecoder.decode(v, "UTF-8") }.toMap
		}
		val response = uri.getPath match {
			case "/portal/locations" =>
				Context.Database.withSession { implicit session =>
					logger.debug(s"portal locations: ${param.filterNot{ _._2.trim().isEmpty }.toSeq.map{ case (k,v)=>s"$k=$v"}.mkString(",")}")
					val json = compact(render(param.filterNot{ _._2.trim().isEmpty }
						.foldLeft(Portals.leftJoin(Geohash).on(_.geohash === _.geohash)) { case (table, (key, value)) =>
						key match {
							case "created_at>" =>
								toTimestamp(value) match {
									case Some(tm) => table.filter { case (p, g) => p.createdAt >= tm }
									case None => throw BadRequest(s"invalid date format: $value")
								}
							case "created_at<" =>
								toTimestamp(value) match {
									case Some(tm) => table.filter { case (p, g) => p.createdAt < tm }
									case None => throw BadRequest(s"invalid date format: $value")
								}
							case "late6>" => table.filter { case (p, g) => p.late6 >= value.toInt}
							case "late6<" => table.filter { case (p, g) => p.late6 < value.toInt}
							case "lnge6>" => table.filter { case (p, g) => p.lnge6 >= value.toInt}
							case "lnge6<" => table.filter { case (p, g) => p.lnge6 < value.toInt}
							case "tile_key" => table.filter { case (p, g) => p.tileKey === value}
							case "country" => table.filter { case (p, g) => g.country === value}
							case "state" => table.filter { case (p, g) => g.state === value}
							case "city" => table.filter { case (p, g) => g.city === value}
							case unknown => throw BadRequest(s"unknown parameter: $unknown=$value")
						}
					}.map { case (p, g) => (p.id, p.title, p.image, p.late6, p.lnge6, p.createdAt, g.country.?, g.state.?, g.city.?)
					}.run.map { case (id, title, image, lat, lng, createdAt, country, state, city) =>
						("id" -> id) ~
							("title" -> title) ~
							("image" -> image) ~
							("latlng" -> List(lat / 1e6, lng / 1e6)) ~
							("country" -> country.getOrElse("")) ~
							("state" -> state.getOrElse("")) ~
							("city" -> city.getOrElse("")) ~
							("created_at" -> toString(createdAt))
					}.toList))
					respond(200, json.getBytes("UTF-8"), "application/json")
				}
			case _ =>
				val path = uri.getPath match {
					case "/" => "/index.html"
					case p => p
				}
				Try { Option(getClass.getClassLoader.getResourceAsStream(s"docroot/$path")) }.toOption.flatten match {
					case Some(in) =>
						val binary = Iterator.continually{ in.read }.takeWhile{ _ >= 0 }.map{ _.toByte }.toArray
						in.close()
						val ct = if(path.endsWith(".html")) "text/html"
						else if(path.endsWith(".png")) "image/png"
						else if(path.endsWith(".css")) "text/css"
						else "application/octet-stream"
						respond(200, binary, ct)
					case None =>
						error(404, "not found")
				}
		}
		Future.value(response)
	} catch {
		case BadRequest(msg) =>
			logger.debug(s"400 Bad Request: $msg")
			Future.value(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST))
		case ex:Throwable =>
			logger.error("", ex)
			Future.value(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR))
	}
	private[this] def host(req:HttpRequest):String = {
		Option(req.headers().get("X-Forwarded-For")) match {
			case Some(h) => h.split(",").head.toLowerCase
			case None => Option(req.headers().get("Host")).getOrElse("").toLowerCase
		}
	}

	case class BadRequest(msg:String) extends Exception(msg)

	private[this] def error(code:Int, msg:String) = respond(code, msg.getBytes("UTF-8"), "text/plain")
	private[this] def respond(code:Int, content:Array[Byte], contentType:String) = {
		val response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.valueOf(code))
		response.headers().add("Content-Type", contentType)
		response.headers().add("Content-Length", content.length)
		response.headers().add("Cache-Control", "no-cache")
		response.setContent(ChannelBuffers.copiedBuffer(content))
		response
	}
	private[this] def toTimestamp(value:String):Option[Timestamp] = (if(value.matches("\\d+")) {
		// 2014-12-30 の様な表記が -1 月などと解釈されるようなので
		Seq("yyyyMMddHHmmss", "yyyyMMddHHmm", "yyyyMMddHH", "yyyyMMdd")
	} else {
		Seq(
			"yyyy/MM/dd H:mm:ss", "yyyy-MM-dd H:mm:ss",
			"yyyy/MM/dd H:mm", "yyyy-MM-dd H:mm",
			"yyyy/MM/dd H", "yyyy-MM-dd H",
			"yyyy/MM/dd", "yyyy-MM-dd")
	}).flatMap{ fmt =>
		val df = new SimpleDateFormat(fmt)
		df.setTimeZone(Context.Locale.timezone)
		Try{ new Timestamp(df.parse(value).getTime) }.toOption
	}.headOption.map{ x =>
		logger.debug(s"timestamp: $value -> ${DateFormat.getDateTimeInstance.format(x)}")
		x
	}
	private[this] def toString(value:Timestamp):String = {
		val df = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT)
		df.setTimeZone(Context.Locale.timezone)
		df.format(value)
	}

}
