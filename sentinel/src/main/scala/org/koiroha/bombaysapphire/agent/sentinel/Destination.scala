/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire.agent.sentinel

import java.io._
import java.net._
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

import org.koiroha.bombaysapphire.Batch
import org.koiroha.bombaysapphire.agent.sentinel.xml._
import org.koiroha.bombaysapphire.io.using
import org.slf4j.LoggerFactory
import org.w3c.dom.{Document, Element}

import scala.concurrent.ExecutionContext

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Destination
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * Sentinel が取得した JSON データの転送先。
 *
 * @author Takami Torao
 */
sealed abstract class Destination(elem:Element) {
	assert(elem.getTagName == "sink")
	def enabled:Boolean = elem.attr("enabled").toBoolean
	def enabled_=(e:Boolean) = elem.attr("enabled", e.toString)
	def uri:URI = URI.create(elem.attr("uri"))
	def uri_=(u:URI) = elem.attr("uri", u.toString)
	def `type`:String
	def text:String
	def store(method:String, request:String, response:String, tm:Long):Unit
	def drop():Unit = elem.getParentNode.removeChild(elem)
}

object Destination {
	implicit val _executionContext = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())
	def parse(elem:Element):Destination = {
		assert(elem.getTagName == "sink")
		URI.create(elem.attr("uri")).getScheme match {
			case GarudaAPI.Scheme => new GarudaAPI(elem)
			case LocalFile.Scheme => new LocalFile(elem)
		}
	}
	def create(doc:Document, uri:String):Element = {
		val elem = doc.createElement("sink")
		elem.attr("uri", uri)
		elem.attr("enabled", "false")
		elem
	}
}

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// GarudaAPI
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 *
 * @author Takami Torao
 */
class GarudaAPI(elem:Element) extends Destination(elem) {
	private[this] val logger = LoggerFactory.getLogger(getClass)
	val TimeHeader = "X-Milliseconds-Before"
	implicit val _ex = Destination._executionContext

	def urlPrefix:String = uri.getSchemeSpecificPart
	def urlPrefix_=(value:String) = uri = URI.create(GarudaAPI.Scheme + ":" + value)
	def `type`:String = "garuda"
	def text:String = urlPrefix

	/** 哨戒情報の保存 */
	def store(method:String, request:String, response:String, tm:Long) = scala.concurrent.Future {
		try {
			val url = new URL(s"$urlPrefix/logs/$method")
			val con = url.openConnection().asInstanceOf[HttpURLConnection]
			val b = (request.getBytes(StandardCharsets.UTF_8), response.getBytes(StandardCharsets.UTF_8))
			val buffer = ByteBuffer.allocate(4 + b._1.length + 4 + b._2.length)
			buffer.putInt(b._1.length)
			buffer.put(b._1)
			buffer.putInt(b._2.length)
			buffer.put(b._2)
			con.setDoOutput(true)
			con.setDoInput(true)
			con.setRequestProperty("Content-Type", "application/octet-stream")
			con.setRequestProperty("Content-Length", buffer.limit().toString)
			con.setRequestProperty(TimeHeader, (System.currentTimeMillis() - tm).toString)
			con.setRequestMethod("POST")
			val out = con.getOutputStream
			out.write(buffer.array())
			out.flush()
			con.getResponseCode match {
				case code if code == 200 => None
				case code if code >= 500 =>
					logger.warn(s"fail to send data, waiting retry...: $code ${con.getResponseMessage}")
					Batch.runAfter(30 * 1000L) {store(method, request, response, tm)}
				case code if code >= 400 =>
					logger.error(s"fail to send data: $code ${con.getResponseMessage}")
			}
		} catch {
			case ex:MalformedURLException =>
				logger.error(s"invalid garuda url: $urlPrefix", ex)
			case ex:ConnectException =>
				logger.warn(s"fail to send data, waiting retry...", ex)
				Batch.runAfter(30 * 1000L) {store(method, request, response, tm)}
			case ex:IOException =>
				logger.warn(s"fail to send data, waiting retry...", ex)
				Batch.runAfter(30 * 1000L) {store(method, request, response, tm)}
		}
	}
}

object GarudaAPI {
	val Scheme = "garuda"
	def create(doc:Document, url:String):Element = {
		val elem = doc.createElement(GarudaAPI.Scheme)
		elem.attr("url", url)
		elem
	}
}

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// LocalFile
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 *
 * @author Takami Torao
 */
class LocalFile(elem:Element) extends Destination(elem) {
	def filename:String = new File(uri).getCanonicalPath
	def filename_=(value:String) = uri = new File(value).toURI
	def `type`:String = "file"
	def text:String = filename

	def store(method:String, request:String, response:String, tm:Long) = using(new FileOutputStream(filename, true)){ os =>
		val channel = os.getChannel
		val lock = channel.lock()
		val out = new PrintWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8))
		try {
			out.println(f"=== $tm%tF $tm%tT.$tm%tL $tm%tZ $method")
			out.println(request)
			out.println("---")
			out.println(response)
		} finally {
			out.flush()
			lock.release()
		}
	}
}

object LocalFile {
	val Scheme = "file"
	def create(doc:Document, file:String):Element = {
		val elem = doc.createElement(LocalFile.Scheme)
		elem.attr("path", file)
		elem
	}
}