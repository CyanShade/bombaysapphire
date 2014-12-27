/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire.tools

import org.koiroha.bombaysapphire.DBAccess
import org.koiroha.bombaysapphire.schema.Tables
import scala.slick.driver.PostgresDriver.simple._

import scala.io.Source

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// GeocodeImport
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
object GeocodeImport extends App with DBAccess {
	db.withSession { implicit session =>
		Tables.Geohash.delete
		val in = Thread.currentThread().getContextClassLoader.getResourceAsStream("geohash5.csv")
		Source.fromInputStream(in).getLines().foreach{ line =>
				val Array(geohash, latE6, lngE6, state, _, city1, city2) = line.split(",").take(7)
				Tables.Geohash.map{ g =>
					(g.geohash, g.late5, g.lnge5, g.country, g.state, g.city)
				}.insert(
				    (geohash, (latE6.toDouble * 1000000).toInt, (lngE6.toDouble * 1000000).toInt, "JP", state, city1+city2)
					)
		}
	}
}
