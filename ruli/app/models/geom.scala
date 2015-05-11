/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package models

import java.io.{File, FileInputStream, InputStream}
import java.util.zip.ZipFile

import org.slf4j.LoggerFactory
import org.xml.sax.InputSource

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

		// ============================================================================================
		/**
		 * `((x1,y1),...)` 形式の文字列から `Area` を生成します。これは PostgreSQL の `polygon` 型データ文字列
		 * と互換性があります。
		 * @param data データ文字列
		 * @return
		 */
		def fromString(data:String):Option[Polygon] = if(! data.startsWith("((") || ! data.endsWith("))")){
			None
		} else {
			Some(Polygon(data.substring(2, data.length - 2).split("\\)\\s*,\\s*\\(").map{ ll =>
				val Array(lat, lng) = ll.split(",", 2)
				LatLng(lat.toDouble, lng.toDouble)
			}))
		}
	}

	case class Area(parts:Seq[Polygon]){
		override def toString = parts.map{ _.toString }.mkString(",")
	}

	object Area {
		private[this] val logger = LoggerFactory.getLogger(classOf[Area])

		def fromString(data:String):Option[Area] = if(! data.startsWith("((") || ! data.endsWith("))")){
			None
		} else {
			Some(Area(data.substring(2, data.length - 2).split("\\)\\)\\s+,\\s*\\(\\(").flatMap{ Polygon.fromString }))
		}

		// ============================================================================================
		/**
		 * KML または KMZ 形式のファイルから領域の読み込み。
		 */
		def fromKML(file:File):Option[Area] =  if(file.getName.endsWith(".kml")) {
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
		 * KML 形式のストリームから領域の読み込み
		 */
		def fromKML(in:InputStream):Option[Area] = fromKML("", in)

		/**
		 * KML 形式のストリームから領域の読み込み
		 */
		private[this] def fromKML(systemId:String, in:InputStream):Option[Area] = {

			val is = new InputSource(systemId)
			is.setByteStream(in)

			val doc = XML.load(is)
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
			Some(Area(polygons))
		}
	}
}
