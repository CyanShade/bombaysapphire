/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire

import java.util.TimeZone

import org.slf4j.LoggerFactory

import scala.slick.driver.PostgresDriver
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
	lazy val Region = {
		val r = (context \ "region").head.asInstanceOf[Elem]
		val north = (r \@ "north").toDouble
		val south = (r \@ "south").toDouble
		val west = (r \@ "west").toDouble
		val east = (r \@ "east").toDouble
		logger.info(s"このアプリケーションの探索領域は '${r.attributes("label")}' ($north,$west)-($south,$east) です.")
		_Region(north, south, west, east)
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

	case class _Region(north:Double, south:Double, west:Double, east:Double) {
		def contains(lat:Double, lng:Double):Boolean = {
			lat >= south && lat <= north && lng >= west && lng <= east
		}
	}

	case class _Database(slickDriver:String, jdbcDriver:String, url:String, username:String, password:String) {
		lazy val db = scala.slick.driver.PostgresDriver.simple.Database.forURL(url, user=username, password=password, driver=jdbcDriver)
		def withSession[T](exec:(PostgresDriver.backend.Session)=>T):T = db.withSession(exec)
	}

	case class _Locale(timezone:TimeZone, language:String) {
	}

}
