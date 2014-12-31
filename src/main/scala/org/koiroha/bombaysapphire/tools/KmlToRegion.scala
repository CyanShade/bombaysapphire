/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire.tools

import java.io.{File, FileInputStream, InputStream}
import java.util.zip.ZipFile

import org.koiroha.bombaysapphire.Context
import org.koiroha.bombaysapphire.io.using
import org.koiroha.bombaysapphire.schema.Tables
import org.slf4j.LoggerFactory
import org.xml.sax.InputSource

import scala.collection.JavaConversions._
import scala.slick.driver.PostgresDriver.simple._
import scala.slick.jdbc.StaticQuery.interpolation
import scala.xml.XML

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// KmlToRegionImport
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * KML (KMZ) ファイルに保存されている多角形を heuristic_regions テーブルに投入する処理。
 *
 * @author Takami Torao
 */
object KmlToRegion {
	val logger = LoggerFactory.getLogger(getClass)

	private[this] val helpString =
		s"""OPTIONS:
			 |
		 """.stripMargin

	def main(args:Array[String]):Unit = {
		val imported = args.map{ f => restore(new File(f)) }.sum
		logger.info(s"$imported 領域をインポートしました.")
	}

	private[this] val FigureNameCSC = """([A-Z]{2})/([^/]*)/([^\.]*)\.(inside|outside)""".r
	private[this] val FigureNameCS = """([A-Z]{2})/([^\.]*)\.(inside|outside)""".r
	private[this] val FigureNameC = """([A-Z]{2})\.(inside|outside)""".r

	private[this] def restore(file:File):Int = {
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
		Context.Database.withTransaction{ implicit session =>
			val is = new InputSource(systemId)
			is.setByteStream(in)
			val doc = XML.load(is)
			(doc \\ "Placemark").map { placemark =>

				// 行政区域の名称を参照
				val (country, state, city, inside) = (placemark \ "name").text.trim() match {
					case FigureNameCSC(c, s, ct, i) => (c, Some(s), Some(ct), i == "inside")
					case FigureNameCS(c, s, i) => (c, Some(s), None, i == "inside")
					case FigureNameC(c, i) => (c, None, None, i == "inside")
					case unknown =>
						logger.error(s"図形に付けられている名前が不正です: $unknown")
						logger.error(s"country{/state{/city}}}.(inside|outside) となるように修正してください.")
						throw new IllegalArgumentException()
				}
				val regionName = (state, city) match {
					case (Some(s), Some(c)) => s"$c, $s $country"
					case (Some(s), None) => s"$s $country"
					case (None, None) => country
					case _ => throw new IllegalStateException("bug!")
				}

				// 既に存在している領域情報を削除
				val side = if(inside) "I" else "O"
				val deleted = Tables.HeuristicRegions
					.filter { hr => hr.country === country &&
					(hr.state === state || (hr.state.isEmpty && state.isEmpty)) &&
					(hr.city === city || (hr.city.isEmpty && city.isEmpty)) && hr.side === side
				}.delete

				// KML から多角形の座標を取得
				val polygons = (placemark \ "Polygon").map { polygon =>
					(polygon \ "outerBoundaryIs" \ "LinearRing" \ "coordinates").text.trim()
						.split("\\s+").map {_.split(",")}.filter {_.length > 0}.map {
						case Array(lng, lat, _) => (lat.toDouble, lng.toDouble)
						case Array(lng, lat) => (lat.toDouble, lng.toDouble)      // altitude 省略時
						case unexpected =>
							logger.error(s"unexpected coordinate values: $unexpected")
							throw new IllegalArgumentException()
					}.map { case (lat, lng) => s"($lat,$lng)"}.mkString("(", ",", ")")
				}

				// 内側の場合、同一行政区階層で重複している領域がないかを検証
				if(inside){
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
				}

				// 全ての多角形を保存
				polygons.zipWithIndex.foreach{ case (coordinates, i) =>
					sqlu"insert into intel.heuristic_regions(country,state,city,side,seq,region) values($country,$state,$city,$side,$i,$coordinates::polygon)".first
				}
				logger.info(s"$regionName を${if(deleted>0) "置き換え" else "追加し" }ました")
			}
		}.size
	}

	object Inventory {
		locally {
			val in = getClass.getResourceAsStream("conf/regions/inventory.xml")
			val regions = XML.load(in)
			in.close()
		}
	}
	case class Country(code:String)
	case class State(name:String)
	case class City(name:String)
}
