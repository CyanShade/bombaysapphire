/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire

import java.io.{InputStream, FileInputStream, File}
import java.util.zip.ZipFile

import org.koiroha.bombaysapphire.geom.{Polygon, LatLng, AdministrativeDistrict}
import org.slf4j.LoggerFactory
import org.xml.sax.InputSource

import scala.collection.JavaConversions._
import scala.xml.XML

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// KML
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
object KML {
	private[this] val logger = LoggerFactory.getLogger(getClass.getName)

	/** KML または KMZ 形式のファイルから領域の読み込み */
	def fromFile(file:File):Seq[AdministrativeDistrict] = {
		if(file.getName.endsWith(".kml")) {
			io.using(new FileInputStream(file)){ in => fromKML(file.toString, in) }
		} else if(file.getName.endsWith(".kmz")) {
			io.using(new ZipFile(file)){ zfile =>
				zfile.entries().collectFirst {
					case e if e.getName == "doc.kml" =>
						fromKML(file.toString + "#doc.kml", zfile.getInputStream(e))
				} match {
					case Some(e) => e
					case None =>
						logger.error(s"KMZファイルフォーマットが不正です: ${file.getName}")
						throw new IllegalArgumentException(file.getName)
				}
			}
		} else {
			logger.error(s"未対応のファイル拡張子です: ${file.getName}")
			throw new IllegalArgumentException(file.getName)
		}
	}

	/** KML 形式のストリームから領域の読み込み */
	def fromInputStream(in:InputStream):Seq[AdministrativeDistrict] = fromKML("", in)

	/** KML 形式のストリームから領域の読み込み */
	private[this] def fromKML(systemId:String, in:InputStream):Seq[AdministrativeDistrict] = {
		val FigureNameCSC = """([A-Z]{2})/([^/]*)/([^\.]*)""".r
		val FigureNameCS = """([A-Z]{2})/([^\.]*)""".r
		val FigureNameC = """([A-Z]{2})""".r

		val is = new InputSource(systemId)
		is.setByteStream(in)

		val doc = XML.load(is)
		(doc \\ "Placemark").map { placemark =>

			// 行政区域の名称を参照
			val (country, state, city) = (placemark \ "name").text.trim() match {
				case FigureNameCSC(c, s, ct) => (c, Some(s), Some(ct))
				case FigureNameCS(c, s) => (c, Some(s), None)
				case FigureNameC(c) => (c, None, None)
				case unknown =>
					logger.error(s"図形に付けられている名前が不正です: $unknown")
					logger.error(s"country{/state{/city}}} となるように修正してください.")
					throw new IllegalArgumentException()
			}
			val regionName = (state, city) match {
				case (Some(s), Some(c)) => s"$c, $s $country"
				case (Some(s), None) => s"$s $country"
				case (None, None) => country
				case _ => throw new IllegalStateException("bug!")
			}

			// KML から多角形の座標を取得
			val polygons = (placemark \ "Polygon").map { polygon =>
				(polygon \ "outerBoundaryIs" \ "LinearRing" \ "coordinates").text.trim()
					.split("\\s+").map {_.split(",")}.filter {_.length > 0}.map {
					case Array(lng, lat, _) => (lat.toDouble, lng.toDouble)
					case Array(lng, lat) => (lat.toDouble, lng.toDouble)      // altitude 省略時
					case unexpected =>
						logger.error(s"unexpected coordinate values: $unexpected")
						throw new IllegalArgumentException()
				}.map { case (lat, lng) => LatLng(lat, lng) }.toSeq
			}.map{ p => Polygon(p) }

			AdministrativeDistrict(country, state.getOrElse(""), city.getOrElse(""), polygons)
		}
	}
}
