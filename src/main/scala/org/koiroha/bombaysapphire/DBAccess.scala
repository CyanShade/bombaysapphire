/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire

import scala.slick.driver.PostgresDriver.simple._

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// DBAccess
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * ```
 * import scala.slick.driver.PostgresDriver.simple._
 * ```
 * @author Takami Torao
 */
trait DBAccess {
	object Config {
		val slickDriver = "scala.slick.driver.PostgresDriver"
		val jdbcDriver = "org.postgresql.Driver"
		val url = "jdbc:postgresql://localhost:5433/bombaysapphire"
		val username = "postgres"
		val password = "postgres"
	}

	lazy val db = Database.forURL(Config.url, user=Config.username, password=Config.password, driver=Config.jdbcDriver)
}
