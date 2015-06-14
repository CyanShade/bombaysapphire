/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire

import java.io._
import java.net.{HttpURLConnection, URL}
import java.util.zip.{ZipFile, ZipInputStream}

import org.slf4j.LoggerFactory
import org.xml.sax.InputSource

import scala.annotation.tailrec
import scala.collection.JavaConversions._
import scala.xml.XML

package object geom {

	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	// 緯度/経度
	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	/**
	 * 緯度/経度を表す値。
	 * @param lat 緯度
	 * @param lng 経度
	 */
	case class LatLng(lat:Double, lng:Double)

	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	// 多角形
	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	/**
	 * ジオメトリ上の多角形。
	 * @param points 閉じた点
	 */
	case class Polygon(points:Seq[LatLng]){
		override def toString = points.map{ ll => s"(${ll.lat},${ll.lng})" }.mkString("(", ",", ")")
	}

	object Polygon {
		private[this] val Prefix = "(("
		private[this] val Suffix = "))"

		// ============================================================================================
		/**
		 * `((x1,y1),...)` 形式の文字列から `Polygon` を生成する。これは PostgreSQL の `polygon` 型データ文字列
		 * と互換性がある。
		 * @param data データ文字列
		 * @return
		 */
		def fromString(data:String):Option[Polygon] = if(! data.startsWith(Prefix) || ! data.endsWith(Suffix)){
			None
		} else {
			Some(Polygon(data.substring(Prefix.length, data.length - Suffix.length).split("\\)\\s*,\\s*\\(").map{ ll =>
				val Array(lat, lng) = ll.split(",", 2)
				LatLng(lat.toDouble, lng.toDouble)
			}))
		}
	}

	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	// 領域
	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	/**
	 * 領域を示す複数の多角形で構成される領域。
	 * @param parts 閉じた点
	 */
	case class Region(parts:Seq[Polygon]){
		override def toString = parts.map{ _.toString }.mkString(";")
	}

	object Region {
		private[this] val logger = LoggerFactory.getLogger(classOf[Region])

		// ============================================================================================
		// 文字列の解析
		// ============================================================================================
		/**
		 * 指定された文字列から Region を生成。
		 * @param data
		 * @return
		 */
		def fromString(data:String):Option[Region] = if(! data.startsWith("((") || ! data.endsWith("))")){
			None
		} else {
			Some(Region(data.substring(2, data.length - 2).split("\\)\\)\\s+,\\s*\\(\\(").flatMap{ Polygon.fromString }))
		}

		// ============================================================================================
		/**
		 * KML または KMZ 形式のファイルから領域の読み込み。
		 */
		def fromKML(file:File):Option[Region] =  if(file.getName.endsWith(".kml")) {
			services.io.using(new FileInputStream(file)){ in => fromKML(file.toString, in) }
		} else if(file.getName.endsWith(".kmz")) {
			services.io.using(new ZipFile(file)){ zfile =>
				zfile.entries().collectFirst {
					case e if e.getName == "doc.kml" =>
						fromKML(file.toString + "#doc.kml", zfile.getInputStream(e))
				} match {
					case Some(e) => e
					case None =>
						logger.error(s"KMZファイルフォーマットが不正です: ${file.getName}")
						None
				}
			}
		} else {
			logger.error(s"未対応のファイル拡張子です: ${file.getName}")
			None
		}

		// ============================================================================================
		/**
		 * KML または KMZ 形式のファイルから領域の読み込み。
		 */
		def fromKML(url:URL):Option[Region] = {
			val kmz:(InputStream)=>Option[InputStream] = { in => findDocXml(new ZipInputStream(in)) }
			val kml:(InputStream)=>Option[InputStream] = { in => Some(in) }

			val con = getConnection(url)
			io.using(new PushbackInputStream(con.getInputStream)) { in =>
				val head = in.read()
				in.unread(head)
				val filter: Option[(InputStream) => Option[InputStream]] = head match {
					// 先頭のバイト値から推測
					case 'P' => Some(kmz)
					case '<' => Some(kml)
					case _ =>
						// Content-Type から推測
						Option(con.getContentType).flatMap { ct =>
							ct.takeWhile {_ == ';'}.trim.toLowerCase match {
								case "application/vnd.google-earth.kml+xml" => Some(kml)
								case "text/xml" => Some(kml)
								case "application/vnd.google-earth.kmz" => Some(kmz)
								case "application/zip" => Some(kmz)
								case _ => None
							}
						}.orElse {
							// 拡張子から推測
							url.getPath.reverse.takeWhile {_ != '.'}.reverse match {
								case "kml" => Some(kml)
								case "kmz" => Some(kmz)
								case _ => None
							}
						}
				}

				filter match {
					case Some(f) =>
						f(in) match {
							case Some(is) =>
								fromKML(url.toString, is)
							case None =>
								// KMZ ではない ZIP ファイル
								logger.error(s"KMZファイルフォーマットが不正です: $url")
								None
						}
					case None =>
					// 推測不能
						logger.error(s"未対応のファイル拡張子です: $url")
						None
				}
			}
		}

		// ============================================================================================
		/**
		 * KML 形式のストリームから領域の読み込み
		 */
		private[this] def fromKML(systemId:String, in:InputStream):Option[Region] = {

			val is = new InputSource(systemId)
			is.setByteStream(in)

			val doc = XML.load(is)
			if(doc.label != "kml"){
				logger.error(s"KML ファイルではありません: ${doc.label}")
				None
			} else {
				(doc \\ "Document" \ "NetworkLink" \ "Link" \ "href").headOption.map{ _.text } match {
					case Some(url) =>
						// ネットワークリンクの場合はたどる
						fromKML(new URL(url))
					case None =>
						val polygons = (doc \\ "Placemark").flatMap { placemark =>

							// KML から多角形の座標を取得
							(placemark \ "Polygon").map { polygon =>
								(polygon \ "outerBoundaryIs" \ "LinearRing" \ "coordinates").text.trim()
									.split("\\s+").map {_.split(",")}.filter {_.length > 0}.map {
									case Array(lng, lat, _) => LatLng(lat.toDouble, lng.toDouble)
									case Array(lng, lat) => LatLng(lat.toDouble, lng.toDouble)      // altitude 省略時
									case unexpected =>
										logger.error(s"unexpected coordinate values: $unexpected")
										return None
								}.toSeq
							}.map{ p => Polygon(p) }
						}
						Some(Region(polygons))
				}
			}
		}
	}

	@tailrec
	private[this] def findDocXml(in:ZipInputStream):Option[InputStream] = {
		Option(in.getNextEntry) match {
			case Some(e) if e.getName == "doc.kml" => Some(in)
			case Some(e) =>
				Iterator.continually {in.read()}.takeWhile {_ >= 0}
				in.closeEntry()
				findDocXml(in)
			case None => None
		}
	}

	private[this] def getConnection(url:URL, maxRedirects:Int = 10):HttpURLConnection = {
		val con = url.openConnection().asInstanceOf[HttpURLConnection]
		con.getResponseCode match {
			case HttpURLConnection.HTTP_OK => con
			case c if c == HttpURLConnection.HTTP_MOVED_TEMP || c == HttpURLConnection.HTTP_MOVED_PERM =>
				if(maxRedirects <= 0){
					throw new IOException(s"too many redirects: $url")
				}
				getConnection(new URL(con.getHeaderField("Location")), maxRedirects - 1)
			case unknown =>
				throw new IOException(s"$unknown ${con.getResponseMessage}")
		}
	}
}
