/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire.garuda.batch

import java.sql.Timestamp

import org.json4s._
import org.json4s.native.JsonMethods._
import org.koiroha.bombaysapphire.garuda._
import org.koiroha.bombaysapphire.garuda.schema.Tables
import org.slf4j.LoggerFactory

import scala.slick.driver.PostgresDriver.simple._
import scala.slick.jdbc.StaticQuery.interpolation

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Farms
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
object Farms {
	private[this] val logger = LoggerFactory.getLogger(getClass.getName.dropRight(1))

	def activities(context:Context) = context.database.withSession { implicit _session =>
		Tables.Farms.map{ _.id }.list.foreach{ farmId =>
			val portalIds = Tables.FarmPortals.filter{ _.farmId === farmId }.map{ _.portalId }.list
			val limit = System.currentTimeMillis() - (60 * 60 * 1000L)
			portalIds.flatMap { portalId =>
				sql"select level,health,team,resonators,mods,created_at from intel.portal_state_logs where portal_id=$portalId and resonators is not null and mods is not null order by created_at desc limit 1".as[(Int, Int, String, String, String, Timestamp)].firstOption.map {
					case (ll, health, st, resonators, sm, ct) =>
						val res = parse(resonators) match {
							case JArray(array) => PortalDetails.Resonator.parseOld(array)
							case _ =>
								logger.warn(s"unexpected resonators format: $resonators")
								List()
						}
						val mods = parse(sm) match {
							case JArray(array) => PortalDetails.Mod.parseOld(array).flatten
							case _ =>
								logger.warn(s"unexpected mods format: $resonators")
								List()
						}
						val p8 = if(res.count{ _.level == 8 } >= 7) 1 else 0
						val team = Team(st) match {
							case Some(Resistance) => Faction(1, 0)
							case Some(Enlightened) => Faction(0, 1)
							case _ =>
								logger.warn(s"unexpected mods format: $resonators")
								Faction(0, 0)
						}
						val shielding = mods.map{ _.mitigation }.sum
						// MultiHack: 1つ目が100%、2つ目以降が50%
						val hack = mods
							.map{ _.burnoutInsulation }
							.sorted.reverse
							.zipWithIndex.map{ case (h, 0) => h; case (h, _) => h / 2 }.sum
						val hackSpeed = 1.0 - mods
							.map{ _.hackSpeed }
							.filter{ _ != 0 }
							.sorted.reverse
							.zipWithIndex.map{ case (h, 0) => h; case (h, _) => h / 2 }
							.map{ hs => 1.0 - (hs / 1000000.0) }.reduceLeftOption{ _ * _ }.getOrElse(0.0)
						val level = res.map{ _.level }.sum
						P(count=1,
							faction=team,
							p8=team * p8,
							level=team * level,
							res=team * res.size,
							mod=team * mods.size,
							mitigation=team * shielding,
							cooldown=hackSpeed.toInt, hack=hack,
							measuredAt=ct.getTime
						)
				}
			}.filter{ _.measuredAt > limit }.reduceLeftOption{ _ + _ } match {
				case Some(p) =>
					Tables.FarmActivities
						.map{ l => (l.farmId,
						l.strictPortalCount, l.portalCount, l.portalCountR, l.portalCountE,
						l.p8ReachR, l.p8ReachE,
						l.avrResLevelR, l.avrResLevelE,
						l.avrResonatorR, l.avrResonatorE,
						l.avrModR, l.avrModE,
						l.avrMitigationR, l.avrMitigationE,
						l.avrCooldownRatio, l.additionalHack,
						l.measuredAt)}
						.insert((farmId,
						portalIds.size, p.count, p.faction.r, p.faction.e,
						p.p8.r, p.p8.e,
						p.avrResLevel.r, p.avrResLevel.e,
						p.avrResonators.r, p.avrResonators.e,
						p.avrMods.r, p.avrMods.e,
						p.avrMitigation.r, p.avrMitigation.e,
						p.avrCooldownRatio, p.hack,
						p.avrMeasuredAt))
					val newId = Tables.FarmActivities.filter{ _.farmId === farmId }
						.sortBy{ _.measuredAt.desc }.take(1)
						.map{ _.id }.first
					Tables.Farms.filter{ _.id === farmId}.map{ _.latestActivity }.update(Some(newId)).run
				case None => ()
			}
		}
	}

	case class Faction(r:Int, e:Int){
		def +(o:Faction) = Faction(r + o.r, e + o.e)
		def *(i:Int) = Faction(r * i, e * i)
		def /(f:Faction) = FFaction(
			if(r==0 && f.r==0) 0 else (r / f.r.toFloat),
			if(e==0 && f.e==0) 0 else (e / f.e.toFloat))
	}
	case class FFaction(r:Float, e:Float)
	case class P(count:Int, faction:Faction, p8:Faction, level:Faction, res:Faction, mod:Faction, mitigation:Faction, cooldown:Int, hack:Int, measuredAt:Long){
		lazy val avrResLevel = level / (faction * 8)
		lazy val avrResonators = res / faction
		lazy val avrMods = mod / faction
		lazy val avrMitigation = mitigation / faction
		lazy val avrCooldownRatio = cooldown / count.toFloat
		lazy val avrMeasuredAt = new Timestamp(measuredAt / count)
		def +(o:P) = P(count+o.count, faction+o.faction, p8+o.p8, level+o.level, res+o.res, mod+o.mod, mitigation+o.mitigation, cooldown+o.cooldown, hack+o.hack, measuredAt+o.measuredAt)
	}

}
