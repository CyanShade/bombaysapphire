/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire

import java.util.TimeZone

import org.koiroha.bombaysapphire.schema.Tables
import org.slf4j.LoggerFactory

import scala.slick.driver.PostgresDriver
import scala.slick.driver.PostgresDriver.simple._
import scala.slick.jdbc.StaticQuery.interpolation
import scala.xml.{Elem, XML}

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Context
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
object Context {
	private[this] val logger = LoggerFactory.getLogger(getClass)

	private[this] val context = locally {
		val in = getClass.getClassLoader.getResourceAsStream("context.xml")
		val root = XML.load(in)
		in.close()
		root
	}

	/** 検索避けのためシーザー暗号で簡単な難読化 */
	def decode(s:String):String = s.map{ _ + 3 }.map{ _.toChar }.mkString
	/** 検索避けのためシーザー暗号で簡単な難読化 */
	def encode(s:String):String = s.map{ _ - 3 }.map{ _.toChar }.mkString

	/** かのサイトのホスト名 */
	def RemoteHost = decode("ttt+fkdobpp+`lj")   // ホスト名
	/** かのサイトのホストアドレス */
	def RemoteAddress = decode("4/+.1+/16+.5-")  // IP アドレス

	/**
	 * このアプリケーションが調査の対象とする領域。
	 */
	lazy val Region:_Region = {
		val r = (context \ "region").head.asInstanceOf[Elem]
		logger.info(s"このアプリケーションの探索領域は '${r.attributes("label")}' です.")
		(r \@ "country", r\@ "state", r \@ "city") match {
			case ("", "", "") =>
				val n = (r \@ "north").toDouble
				val s = (r \@ "south").toDouble
				val w = (r \@ "west").toDouble
				val e = (r \@ "east").toDouble
				_RectRegion(n, s, e, w)
			case (country, state, city) =>
				val ids = Context.Database.withSession{ implicit session =>
					sql"select id from intel.heuristic_regions where country=$country and (state=$state or ($state='' and state is null)) and (city=$city or ($city='' and city is null)) and side='O'".as[Int].list
				}
				if(ids.isEmpty) {
					throw new IllegalStateException(s"対応する調査領域が heuristic_regions に定義されていません: [$country][$state][$city]")
				} else {
					_DBRegion(ids)
				}
		}
	}

	/**
	 * このアプリケーションのデータベース。
	 * ```
	 * import scala.slick.driver.PostgresDriver.simple._
	 * ```
	 */
	lazy val Database = {
		val d = (context \ "database").head.asInstanceOf[Elem]
		_Database(
			"scala.slick.driver.PostgresDriver", "org.postgresql.Driver",
			d \@ "url", d \@ "username", d \@ "password"
		)
	}

	/** ロケール */
	lazy val Locale = {
		val d = (context \ "locale").head.asInstanceOf[Elem]
		_Locale(TimeZone.getTimeZone(d \@ "timezone"), d \@ "language")
	}

	trait _Region {
		val north:Double
		val south:Double
		val east:Double
		val west:Double
		def contains(lat:Double, lng:Double):Boolean
	}

	case class _RectRegion(override val north:Double, override val south:Double, override val east:Double, override val west:Double) extends _Region {
		override def contains(lat:Double, lng:Double):Boolean = {
			lat >= south && lat <= north && lng >= west && lng <= east
		}
	}

	case class _DBRegion(ids:Seq[Int]) extends _Region{
		private[this] val poly = Context.Database.withSession{ implicit session =>
			Tables.HeuristicRegions.filter{ _.id.inSet(ids) }.map{ _.region }.run.flatMap{ poly =>
				if(poly.startsWith("((") && poly.endsWith("))")){
					poly.substring(2, poly.length - 2).split("\\),\\(")
						.map{ _.split(",", 2).map{ _.toDouble } }.map{ case Array(lat,lng) => (lat,lng) }.toSeq
				} else throw new IllegalArgumentException(s"unexpected polygon data format: $poly")
			}
		}
		val (north, east) = poly.reduceLeft{ (l0, l1) => (math.max(l0._1, l1._1), math.max(l0._2, l1._2)) }
		val (south, west) = poly.reduceLeft{ (l0, l1) => (math.min(l0._1, l1._1), math.min(l0._2, l1._2)) }
		def contains(lat:Double, lng:Double):Boolean = Context.Database.withSession{ implicit session =>
			sql"select count(*) from intel.heuristic_regions where id in (#${ids.mkString(",")}) and point '(#$lat,#$lng)' @ region".as[Int].first > 0
		}
	}

	case class _Database(slickDriver:String, jdbcDriver:String, url:String, username:String, password:String) {
		lazy val db = scala.slick.driver.PostgresDriver.simple.Database.forURL(url, user=username, password=password, driver=jdbcDriver)
		def withSession[T](exec:(PostgresDriver.backend.Session)=>T):T = db.withSession(exec)
		def withTransaction[T](exec:(PostgresDriver.backend.Session)=>T):T = db.withTransaction(exec)
	}

	case class _Locale(timezone:TimeZone, language:String) {
	}

}
