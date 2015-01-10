/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire.garuda

import java.io.{FileInputStream, File}
import java.util.{Properties, Locale}

import scala.slick.driver.PostgresDriver.simple._
import scala.collection.JavaConversions._

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Context
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class Context(config:Map[String,String]) {

	val garuda = new Garuda(this)

	val server = new Server(this,
		config.getOrElse("server.address", "localhost"),
		config.get("server.port").map{ _.toInt }.getOrElse(80)
	)

	object databaseConfig {
		val url = config("database.url")
		val username = config("database.username")
		val password = config("database.password")
		val driver = classOf[org.postgresql.Driver].getName
	}
	/**
	 * ```
	 * import scala.slick.driver.PostgresDriver.simple._
	 * ```
	 */
	val database = Database.forURL(databaseConfig.url,
		user = databaseConfig.username, password = databaseConfig.password,
		driver = databaseConfig.driver)

	object locale {
		/**
		 * 対応言語。GooCode API 問い合わせ時のレスポンス言語指定に使用される。
		 */
		val language = config.getOrElse("locale.language", Locale.getDefault.getLanguage)
	}


	val geocode = new GeoCode(this)
}

object Context {
	def apply(file:File):Context = org.koiroha.bombaysapphire.io.using(new FileInputStream(file)){ in =>
		val prop = new Properties()
		prop.load(in)
		new Context(prop.toMap)
	}
}