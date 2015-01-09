/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire

import java.awt.geom.{Path2D, Point2D, Rectangle2D}
import java.io.{File, FileInputStream, InputStream}
import java.util.zip.ZipFile

import org.slf4j.LoggerFactory
import org.xml.sax.InputSource

import scala.collection.JavaConversions._
import scala.language.reflectiveCalls
import scala.xml.XML

package object io {
	def using[T <: {def close():Unit},R](r:T)(exec:(T)=>R):R = try {
		exec(r)
	} finally {
		r.close()
	}
}

package object geom {
	type Latitude = Double
	type Longitude = Double

	case class LatLng(latitude: Latitude, longitude: Longitude) {
		assert(latitude >= -90 && latitude <= 90)
		assert(longitude >= -180 && longitude <= 180)

		def this(latE6: Int, lngE6: Int) = this(latE6 / 1e6, lngE6 / 1e6)
	}

	trait Shape {
		def contains(point: LatLng): Boolean = contains(point.latitude, point.longitude)

		def contains(lat: Latitude, lng: Longitude): Boolean

		def intersects(rect: Rectangle): Boolean

		def rectangle: Rectangle
	}

	case class Polygon(points: Seq[LatLng]) extends Shape {
		assert(points.size >= 3)
		private[this] val polygon = locally {
			val path = new Path2D.Double()
			path.moveTo(points(0).latitude, points(0).longitude)
			points.foreach { case LatLng(lat, lng) => path.lineTo(lat, lng)}
			if (points(0) != points(points.size - 1)) {
				path.lineTo(points(0).latitude, points(0).longitude)
			}
			path
		}

		def contains(lat: Latitude, lng: Longitude): Boolean = polygon.contains(new Point2D.Double(lat, lng))

		def intersects(rect: Rectangle): Boolean = this.polygon.intersects(rect.rect)

		lazy val rectangle: Rectangle = {
			val rect = polygon.getBounds2D
			Rectangle(rect.getY + rect.getHeight, rect.getX + rect.getWidth, rect.getY, rect.getX)
		}
	}

	case class Rectangle(north: Latitude, east: Longitude, south: Latitude, west: Longitude) extends Shape {
		assert(north >= south)
		assert(east >= west)
		private[bombaysapphire] val rect = new Rectangle2D.Double(west, south, east - west, north - south)

		def contains(lat: Latitude, lng: Longitude): Boolean = rect.contains(new Point2D.Double(lat, lng))

		def intersects(r: Rectangle): Boolean = this.rect.intersects(r.rect)

		def rectangle = this

		def union(r: Rectangle): Rectangle = {
			val rx = r.rect.createUnion(this.rect)
			Rectangle(rx.getY + rx.getHeight, rx.getX + rx.getWidth, rx.getY, rx.getX)
		}

		lazy val center: LatLng = LatLng((north + south) / 2.0, (east - west) / 2.0)
	}

	case class Region(name: String, shapes: Seq[Shape]) extends Shape {
		def contains(lat: Latitude, lng: Longitude): Boolean = shapes.exists {
			_.contains(lat, lng)
		}

		def intersects(rect: Rectangle): Boolean = shapes.exists {
			_.intersects(rect)
		}

		def rectangle: Rectangle = shapes.map {
			_.rectangle
		}.reduceLeft { (a, b) => a.union(b)}
	}

	case class AdministrativeDistrict(country: String, state: String, city: String, shapes: Seq[Shape]) {
		val toRegion = Region(s"$country $state $city", shapes)
	}

	object AdministrativeDistrict {
		private[this] val logger = LoggerFactory.getLogger(classOf[Region])

		/** KML または KMZ 形式のファイルから領域の読み込み */
		def fromKML(file:File):Seq[AdministrativeDistrict] = {
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
		def fromKML(in:InputStream):Seq[AdministrativeDistrict] = fromKML("", in)

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
}
