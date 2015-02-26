/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire.garuda.tools

import java.io.File

import org.koiroha.bombaysapphire.{BombaySapphire => BS}
import org.koiroha.bombaysapphire.garuda.Context
import org.koiroha.bombaysapphire.garuda.schema.Tables
import org.slf4j.LoggerFactory
import scala.collection.mutable
import scala.slick.driver.PostgresDriver.simple._

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// FindMostPortalPosition
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class FindMostPortalPosition(context:Context) {
	import FindMostPortalPosition._
	def find(r:Double):Seq[(Int,Seq[(Double,Double)])] = context.database.withSession{ implicit _session =>
		val ps = Tables.Portals.innerJoin(Tables.Geohash)
			.on{ _.geohash === _.geohash }
			.filter{ case (p,g) => g.state === "東京都" }
			.map{ case (p,g) => (p.late6, p.lnge6) }
			.list.map{ p => (p._1 / 1e6, p._2 / 1e6) }
		logger.debug(f"found ${ps.size}%,d portal positions")
		val (maxLat, maxLng, minLat, minLng) = (ps.map{ _._1 }.max, ps.map{ _._2 }.max, ps.map{ _._1 }.min, ps.map{ _._2 }.min)
		logger.debug(f"($maxLat%.6f, $maxLng%.6f, $minLat%.6f, $minLng%.6f)")

		val score = mutable.HashMap[Int,mutable.Buffer[(Double,Double)]]()
		val latUnit = BS.latUnit / 1000.0
		def lngUnit(lat:Double) = BS.lngUnit(lat) / 1000.0
		val r2 = r*r
		for(
			lat <- minLat until(maxLat + latUnit     ) by latUnit;
			lng <- minLng until(maxLng + lngUnit(lat)) by lngUnit(lat)
		) {
			val min = if(score.isEmpty) 0 else score.keySet.min
			val count = ps.map{ case (pLat,pLng) => (math.abs(lat-pLat) / latUnit, math.abs(lng-pLng) / lngUnit(lat)) }
				.filter{ p => p._1 <= r && p._2 <= r }
				.count{ p => p._1 * p._1 + p._2 * p._2 <= r2 }
			if(count >= min && count > 1){
				val buffer = if(score.contains(count)){
					score(count)
				} else {
					val buffer = mutable.Buffer[(Double,Double)]()
					score += count -> buffer
					if(score.size > 5){
						score.remove(min)
					}
					buffer
				}
				buffer.+=((lat, lng))
			}
		}
		score.toSeq
	}
}
object FindMostPortalPosition {
	val logger = LoggerFactory.getLogger(classOf[FindMostPortalPosition])

	/** スキャナ半径[m] */
	val ScannerRange = 40

	def main(args: Array[String]): Unit = {
		val context = Context(new File(args.head))
		val app = new FindMostPortalPosition(context)
		app.find(ScannerRange).take(100).foreach{ case (count,seq) =>
			seq.foreach{ case (lat, lng) =>
				println(f"$lat%.6f\t$lng%.6f\t$count")
			}
		}
	}
}