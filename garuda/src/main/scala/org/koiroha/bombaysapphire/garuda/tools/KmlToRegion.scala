/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire.garuda.tools

import java.io.{File, FileInputStream, InputStream}
import java.util.zip.ZipFile

import org.koiroha.bombaysapphire.garuda.Context
import org.koiroha.bombaysapphire.garuda.schema.Tables
import org.koiroha.bombaysapphire.io.using
import org.slf4j.LoggerFactory
import org.xml.sax.InputSource

import scala.collection.JavaConversions._
import scala.slick.driver.PostgresDriver.simple._
import scala.slick.jdbc.StaticQuery.interpolation
import scala.xml.{Node, XML}

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// KmlToRegionImport
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * KML (KMZ) ファイルに保存されている多角形を heuristic_regions テーブルに投入する処理。
 *
 * @author Takami Torao
 */
class KmlToRegion(context:Context) {
	import org.koiroha.bombaysapphire.garuda.tools.KmlToRegion._

	def restore(file:File):Int = {
		val imported = if(file.getName.endsWith(".kml")) {
			using(new FileInputStream(file)){ in =>
				restore(file.toString, in)
			}
		} else if(file.getName.endsWith(".kmz")) {
			using(new ZipFile(file)){ zfile =>
				zfile.entries().map { e =>
					if(e.getName == "doc.kml") {
						restore(file.toString + "#doc.kml", zfile.getInputStream(e))
					} else 0
				}.sum
			}
		} else {
			logger.error(s"未対応のファイル拡張子です: ${file.getName}")
			throw new IllegalArgumentException(file.getName)
		}
		logger.info(s"ファイル ${file.getName} をインポートしました.")
		imported
	}

	private[this] def restore(systemId:String, in:InputStream):Int = {
		context.database.withTransaction { implicit session =>
			val is = new InputSource(systemId)
			is.setByteStream(in)
			val doc = XML.load(is)
			(doc \ "Document" \ "Folder").map { c =>
				val country = (c \ "name").text.trim()
				(c \ "Folder").map { s =>
					val state = (s \ "name").text.trim()
					(s \ "Folder").map { ct =>
						val city = (ct \ "name").text.trim()
						restore(country, Some(state), Some(city), ct)
					}.sum + restore(country, Some(state), None, s)
				}.sum + restore(country, None, None, c)
			}.sum
		}
	}

	private[this] def restore(country:String, state:Option[String], city:Option[String], folder:Node)(implicit _session:Session):Int = {
		// 既に存在している領域情報を削除
		val side = "I"
		val deleted = Tables.HeuristicRegions
			.filter { hr => hr.country === country &&
			(hr.state === state || (hr.state.isEmpty && state.isEmpty)) &&
			(hr.city === city || (hr.city.isEmpty && city.isEmpty)) && hr.side === side
		}.delete

		// KML から多角形の座標を取得
		val polygons = (folder \ "Placemark").flatMap { placemark =>
			(placemark \ "Polygon" \ "outerBoundaryIs" \ "LinearRing" \ "coordinates").map { coords =>
				coords.text.trim().split("\\s+").map {_.split(",")}.filter {_.length > 0}.map {
					case Array(lng, lat, _) => (lat.toDouble, lng.toDouble)
					case Array(lng, lat) => (lat.toDouble, lng.toDouble) // altitude 省略時
					case unexpected =>
						logger.error(s"unexpected coordinate values: $unexpected")
						throw new IllegalArgumentException()
				}.map { case (lat, lng) => s"($lat,$lng)"}.mkString("(", ",", ")")
			}
		}

		// 同一行政区階層で重複している領域がないかを検証
		val overlap:Seq[(String,String,String)] = (state,city) match {
			case (Some(s), Some(c)) =>
				polygons.flatMap { coordinates =>
					sql"select country,state,city from intel.heuristic_regions where state is not null and city is not null and region && $coordinates::polygon group by country,state,city".as[(String, String, String)].list
				}
			case (Some(s), None) =>
				polygons.flatMap { coordinates =>
					sql"select country,state,'' from intel.heuristic_regions where state is not null and city is null and region && $coordinates::polygon group by country,state".as[(String, String, String)].list
				}
			case (None, None) =>
				polygons.flatMap { coordinates =>
					sql"select country,'','' from intel.heuristic_regions where state is null and city is null and region && $coordinates::polygon group by country".as[(String, String, String)].list
				}
			case _ => throw new IllegalStateException("bug!")
		}
		if(overlap.nonEmpty){
			logger.error(s"指定された領域 $country$state$city は以下の行政区の既存の内包領域と")
			overlap.foreach{ case (c, st, ct) =>
				logger.error(s" $c$st$ct")
			}
			throw new IllegalArgumentException()
		}

		// 多角形を保存
		polygons.zipWithIndex.foreach{ case (coordinates, i) =>
			sqlu"insert into intel.heuristic_regions(country,state,city,side,seq,region) values($country,$state,$city,$side,$i,$coordinates::polygon)".first
		}
		logger.info(s"$country, $state $city を${if(deleted>0) "置き換え" else "追加し" }ました")
		polygons.size
	}
}
object KmlToRegion {
	val logger = LoggerFactory.getLogger(getClass)

	private[this] val helpString =
		s"""OPTIONS:
			 |
		 """.stripMargin

	def main(args: Array[String]): Unit = {
		val context = Context(new File(args.head))
		val imported = args.tail.map { f =>
			val app = new KmlToRegion(context)
			app.restore(new File(f))
		}.sum
		logger.info(s"$imported 領域をインポートしました.")
	}
}