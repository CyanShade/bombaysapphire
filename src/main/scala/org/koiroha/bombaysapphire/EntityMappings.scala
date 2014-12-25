/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire

import org.koiroha.bombaysapphire.Entities._
import org.koiroha.bombaysapphire.Implicit._
import org.koiroha.bombaysapphire.PortalDetails.{Resonator, Mod}
import org.koiroha.bombaysapphire.RegionScoreDetails._
import org.slf4j.LoggerFactory

import scala.util.Try

sealed abstract class Team
object Team {
	private[this] val logger = LoggerFactory.getLogger(classOf[Team])
	def apply(name:String):Option[Team] = name.toLowerCase match {
		case "enlightened" => Some(Enlightened)
		case "resistance" => Some(Resistance)
		case "neutral" => Some(Neutral)
		case _ =>
			logger.warn(s"unknown team name detected: '$name'")
			None
	}
}
case object Resistance extends Team
case object Enlightened extends Team
case object Neutral extends Team

// getGameScore
case class GameScore(enlightened:Int, resistance:Int)
object GameScore {
	private[this] val logger = LoggerFactory.getLogger(classOf[GameScore])
	def apply(value:Any):Option[GameScore] = Select(
			'enlightened -> Path("result", 0).getInt(value),
			'resistance  -> Path("result", 1).getInt(value)
		).build{ GameScore.apply }
}

/**
```
{
  gameScore: [2695797, 6015489],
  scoreHistory: [ [18, 2428288, 5722443], ..., [1, 2821766, 6107432] ],
  topAgents: [{ nick:golco30, team:RESISTANCE}, ...],
  timeToEndOfBaseCycleMs: 2791045.0,
  regionName: "PA01-ALPHA-12",
  regionVertices: [ [3.5769632E7, 1.38719142E8], ... ]
}
```
*/
case class RegionScoreDetails(
	gameScore:GameScore,
	scoreHistory:List[ScoreHistory],
	topAgents:List[TopAgent],
	timeToEndOfBaseCycleMs:Double,
	regionName:String,
	regionVertices:List[RegionVertex]
) {
}
object RegionScoreDetails {
	private[this] val logger = LoggerFactory.getLogger(classOf[RegionScoreDetails])
	case class ScoreHistory(checkpoint:Int, enlightened:Int, resistance:Int)
	case class TopAgent(nick:String, team:Team)
	case class RegionVertex(enlightened:Int, resistance:Int)

	def apply(value:Any):Option[RegionScoreDetails] = Select(
		'gameScore -> Select(
			'enrightened -> Path("result", "gameScore", 0).getInt(value),
			'registance  -> Path("result", "gameScore", 1).getInt(value)
		).build{ GameScore.apply },
		'scoreHistory -> SelectList(Path("result", "scoreHistory").getList(value)).build { x =>
			Select(
				'checkpoint  -> Path(0).getInt(x),
				'enlightened -> Path(1).getInt(x),
				'resistance  -> Path(2).getInt(x)
			).build { ScoreHistory.apply }
		},
		'topAgent -> SelectList(Path("result", "topAgents").getList(value)).build{ x =>
			Select(
				'nick -> Path("nick").getString(x),
				'team -> Path("team").getString(x).flatMap{ Team.apply }
			).build { TopAgent.apply }
		},
		'timeToEndOfBaseCycleMs -> Path("result", "timeToEndOfBaseCycleMs").getDouble(value),
		'regionName -> Path("result", "regionName").getString(value),
		'regionVertices -> SelectList(Path("result", "regionVertices").getList(value)).build { x =>
			Select(
				'enlightened -> Path(0).getInt(x),
				'resistance  -> Path(1).getInt(x)
			).build { RegionVertex.apply }
		}
	).build{ RegionScoreDetails.apply }
}

case class Entities(map:Map[String,MapRegion]){

}
object Entities {
	private[this] val logger = LoggerFactory.getLogger(classOf[Entities])

	case class MapRegion(deletedGameEntityGuids:Seq[String], gameEntities:Seq[GameEntity], error:Option[String]) {
	}
	object MapRegion {
		def apply(deletedGameEntityGuids:Seq[String], gameEntities:Seq[GameEntity]):MapRegion = this(deletedGameEntityGuids, gameEntities, None)
	}
	/**
  [
    435750f30b174f29bbff565839f96816.16,
    1.419100591302E12,
    {
      latE6 -> 3.5683299E7,
      health -> 100.0,
      image -> http://lh4.ggpht.com/MYqhb-GfXf-b-LQJaC_zxTJjnIiqR59JKqQLNXqzTX4aPrPtHzuaMp9fuZ2IBic5anxsH_8s6x7T9aZBvd7b,
      resCount -> 8.0,
      lngE6 -> 1.39794595E8,
      team -> RESISTANCE,
      title -> 北斎作 深川万年床橋下,
      type -> portal,
      ornaments -> List(),
      level -> 6.0
    }
  ], [ 1f7e766f58f34ff6a4530d49bffbfad1.16, 1.419047928042E12, Map(latE6 -> 3.568481E7, health -> 100.0, image -> http://lh5.ggpht.com/wz0sNaylEDKCjkISLNLEZDmebsP408Yu6SbBE2jp77XcEg5RJnyPS49IPQsKj0McLSjekSiDH6rsR-rE3YU, resCount -> 8.0, lngE6 -> 1.39821138E8, team -> ENLIGHTENED, title -> 小名木川クローバー橋, type -> portal, ornaments -> List(), level -> 7.0))
   */
	abstract class GameEntity(guid:String, unknown:Double, `type`:String)
	case class Portal(guid:String, unknown:Double, latE6:Double, health:Int, image:String, resCount:Int, lngE6:Double, team:Team, title:String, `type`:String, ornaments:Seq[String], level:Int) extends GameEntity(guid, unknown, `type`)
	object Portal {
		private[this] val logger = LoggerFactory.getLogger(classOf[Portal])
		def apply(guid:String, unknown:Double, x:Any):Option[Portal] = Select(
			'latE6 -> Path(2, "latE6").getDouble(x),
			'health -> Path(2, "health").getInt(x),
			'image -> Path(2, "image").getString(x),
			'resCount -> Path(2, "resCount").getInt(x),
			'lngE6 -> Path(2, "lngE6").getDouble(x),
			'team -> Path(2, "team").getString(x).flatMap{ Team.apply },
			'title -> Path(2, "title").getString(x),
			'ornaments -> Path(2, "ornaments").getList(x).map{ _.map { _.toString }},
			'level -> Path(2, "level").getInt(x)
		).build{ Portal(guid, unknown, _:Double, _:Int, _:String, _:Int, _:Double, _:Team, _:String, "portal", _:Seq[String], _:Int) }
	}
	case class Region(guid:String, unknown:Double, points:Seq[Point], team:Team, `type`:String) extends GameEntity(guid, unknown, `type`)
	object Region {
		def apply(guid:String, unknown:Double, x:Any):Option[Region] = Select(
			'point -> SelectList(Path(2, "points").getList(x)).build{ m =>
				Select(
					'guid  -> Path("guid").getString(m),
					'latE6 -> Path("latE6").getDouble(m),
					'lngE6 -> Path("lngE6").getDouble(m)
				).build{ Point.apply }
			},
			'team -> Path(2, "team").getString(x).flatMap{ Team.apply }
		).build{ Region(guid, unknown, _:Seq[Point], _:Team, "region") }
	}
	case class Edge(guid:String, unknown:Double, dest:Point, org:Point, team:Team, `type`:String) extends GameEntity(guid, unknown, `type`)
	object Edge {
		def apply(guid:String, unknown:Double, x:Any):Option[Edge] = Select(
			'dest -> Select(
				'dGuid  -> Path(2, "dGuid").getString(x),
				'dLatE6 -> Path(2, "dLatE6").getDouble(x),
				'dLngE6 -> Path(2, "dLngE6").getDouble(x)
			).build{ Point.apply },
			'org -> Select(
				'oGuid  -> Path(2, "oGuid").getString(x),
				'oLatE6 -> Path(2, "oLatE6").getDouble(x),
				'oLngE6 -> Path(2, "oLngE6").getDouble(x)
			).build{ Point.apply },
			'team -> Path(2, "team").getString(x).flatMap{ Team.apply }
		).build{ Edge(guid, unknown, _:Point, _:Point, _:Team, "edge") }
	}
	case class Point(guid:String, latE6:Double, lngE6:Double)

	def apply(value: Any):Option[Entities] = Path("result", "map").getMap(value).flatMap { m =>
		val region = m.toSeq.map{ case (regionId, info) =>
			val regionInfo = Path("error").getString(info) match {
				case Some(err) => Some(MapRegion(Nil, Nil, Some(err)))
				case None =>
					Select(
						'deletedGameEntityGuids -> SelectList(Path("deletedGameEntityGuids").getList(info)).build{ x => Some(x.toString) },
						'gameEntities -> SelectList(Path("gameEntities").getList(info)).build{ x =>
							Select(
								'guid -> Path(0).getString(x),
								'unknown -> Path(1).getDouble(x),
								'type -> Path(2, "type").getString(x)
							).build{ (guid, unknown, `type`) =>
								`type` match {
									case "portal" => Portal(guid, unknown, x)
									case "edge" => Edge(guid, unknown, x)
									case "region" => Region(guid, unknown, x)
									case uk =>
										logger.warn(s"unknown game-entity type: $uk")
										None
								}
							}
						}.map{ _.flatten }
					).build{ MapRegion.apply }
			}
			(regionId.toString, regionInfo)
		}.filter{ _._2.isDefined }.map{ kv => (kv._1, kv._2.get) }.toMap
		if(region.size == 0) None else Some(Entities(region))
	}
}

case class Plext(guid:String, unknown:Double, categories:Int, markup:List[Any], plextType:String, team:Team, text:String)
object Plext {
	private[this] val logger = LoggerFactory.getLogger(classOf[Plext])
	def apply(value:Any):Option[Seq[Plext]] = {
		SelectList(Path("success").getList(value)).build{ x =>
			Select(
				'guid -> Path(0).getString(x),
				'unknown -> Path(1).getDouble(x),
				'categories -> Path(2, "plext", "categories").getInt(x),
				'markup -> Path(2, "plext", "markup").getList(x),
				'plextType -> Path(2, "plext", "plextType").getString(x),
				'team -> Path(2, "plext", "team").getString(x).flatMap{ Team.apply },
				'text -> Path(2, "plext", "text").getString(x)
			).build{ Plext.apply }
		}
	}
}

	/*
--- getPortalDetails ---
{"latE6":3.5697816E7,"health":99.0,"resonators":[{"owner":"materia64","energy":6000.0,"level":8.0},{"owner":"takayuki8695","energy":5993.0,"level":8.0},{"owner":"Ziraiya","energy":5996.0,"level":8.0},{"owner":"banban21","energy":6000.0,"level":8.0},{"owner":"kosuke01","energy":5991.0,"level":8.0},{"owner":"xanadu2000","energy":5986.0,"level":8.0},{"owner":"st39pq28","energy":5951.0,"level":8.0},{"owner":"colsosun","energy":5984.0,"level":8.0}],"image":"http://lh5.ggpht.com/s_rUFvp3UQoEHzPOLHgvkBZqU0ANBOTCwhbYhZHuxdy4xwoENZoFCGN2Cd8WRHMFcARF_K95hs2gUhZnkeg","mods":[{"owner":"xanadu2000","stats":{"REMOVAL_STICKINESS":"70","MITIGATION":"70"},"name":"AXA Shield","rarity":"VERY_RARE"},{"owner":"Ziraiya","stats":{"REMOVAL_STICKINESS":"20","MITIGATION":"30"},"name":"Portal Shield","rarity":"COMMON"},{"owner":"HedgehogPonta","stats":{"REMOVAL_STICKINESS":"30","MITIGATION":"40"},"name":"Portal Shield","rarity":"RARE"},{"owner":"HedgehogPonta","stats":{"REMOVAL_STICKINESS":"30","MITIGATION":"40"},"name":"Portal Shield","rarity":"RARE"}],"resCount":8.0,"lngE6":1.39812408E8,"team":"RESISTANCE","owner":"Ziraiya","title":"美容ポコ","type":"portal","ornaments":[],"level":8.0}
*/
case class PortalDetails(health:Int, image:String, latE6:Double, level:Int, lngE6:Double, mods:Seq[Option[Mod]], ornaments:Seq[String], owner:String, resCount:Int, resonators:Seq[Resonator], team:Team, title:String, `type`:String)
object PortalDetails {
	/**
	 * {
      "name": "AXA Shield",
      "owner": "xanadu2000",
      "rarity": "VERY_RARE",
      "stats": {
        "MITIGATION": "70",
        "REMOVAL_STICKINESS": "70"
	     }
	 }
	 */
	case class Mod(name:String, owner:String, rarity:String, stats:Map[String,String])
	case class Resonator(energy:Int, level:Int, owner:String)
	def apply(value:Any):Option[PortalDetails] = Select(
		'health -> Path("health").getInt(value),
		'image -> Path("image").getString(value),
		'latE6 -> Path("latE6").getDouble(value),
		'level -> Path("level").getInt(value),
		'lngE6 -> Path("lngE6").getDouble(value),
		'mods -> Path("mods").getList(value).map { mods =>
			mods.map { e =>
				if(e == null) None else Select(
					'name -> Path("name").getString(e),
					'owner -> Path("owner").getString(e),
					'rarity -> Path("rarity").getString(e),
					'stats -> Path("stats").getMap(e).map{ _.map{ case (k,v) => k.toString -> v.toString }}
				).build { Mod.apply }
			}
		},
		'ornaments -> Path("ornaments").getList(value).map{ _.map{ _.toString }},
		'owner -> Path("owner").getString(value),
		'resCount -> Path("resCount").getInt(value),
		'resonators -> SelectList(Path("resonators").getList(value)).build{ r =>
			Select(
				'energy -> Path("energy").getInt(r),
				'level -> Path("level").getInt(r),
				'owner -> Path("owner").getString(r)
			).build{ Resonator.apply }
		},
		'team -> Path("team").getString(value).flatMap{ Team.apply },
		'title -> Path("title").getString(value),
		'type -> Path("type").getString(value)
	).build{ PortalDetails.apply }
}


object Implicit {
	private[this] val logger = LoggerFactory.getLogger(classOf[Entities])
	implicit class _Any(value:Any){
		def toJSON:String = {
			def _jsonize(v:Any):String = v match {
				case s:String => "\"" + s + "\""
				case l:List[_] => s"[${l.map{ _jsonize }.mkString(",")}]"
				case m:Map[_,_] => s"{${m.map{k=>s"${_jsonize(k._1)}:${_jsonize(k._2)}"}.mkString(",")}}"
				case i => if(i==null) "null" else i.toString
			}
			_jsonize(value)
		}
	}
	implicit class _Map(values:Traversable[(Symbol,Option[Any])]) {
		def make[T](exec:PartialFunction[List[Any],T]):Option[T] = {
			values.filter{ _._2.isEmpty }.toList match {
				case Nil =>
					try {
						Some(exec(values.map{ _._2.get }.toList))
					} catch {
						case ex:MatchError =>
							logger.warn(s"parameter not match", ex)
							None
					}
				case errs:List[(Symbol,Option[Any])] =>
					logger.warn(s"${errs.map{ case (k,v) => s"$k:$v" }.mkString(", ")}; not specified")
					None
			}
		}
	}
	object Select {
		def apply[P1](s1:(Symbol,Option[P1])) = Select1(s1)
		def apply[P1,P2](s1:(Symbol,Option[P1]),s2:(Symbol,Option[P2])) = Select2(s1,s2)
		def apply[P1,P2,P3](s1:(Symbol,Option[P1]),s2:(Symbol,Option[P2]),s3:(Symbol,Option[P3])) = Select3(s1,s2,s3)
		def apply[P1,P2,P3,P4](s1:(Symbol,Option[P1]),s2:(Symbol,Option[P2]),s3:(Symbol,Option[P3]),s4:(Symbol,Option[P4])) = Select4(s1,s2,s3,s4)
		def apply[P1,P2,P3,P4,P5](s1:(Symbol,Option[P1]),s2:(Symbol,Option[P2]),s3:(Symbol,Option[P3]),s4:(Symbol,Option[P4]),s5:(Symbol,Option[P5])) = Select5(s1,s2,s3,s4,s5)
		def apply[P1,P2,P3,P4,P5,P6](s1:(Symbol,Option[P1]),s2:(Symbol,Option[P2]),s3:(Symbol,Option[P3]),s4:(Symbol,Option[P4]),s5:(Symbol,Option[P5]),s6:(Symbol,Option[P6])) = Select6(s1,s2,s3,s4,s5,s6)
		def apply[P1,P2,P3,P4,P5,P6,P7](s1:(Symbol,Option[P1]),s2:(Symbol,Option[P2]),s3:(Symbol,Option[P3]),s4:(Symbol,Option[P4]),s5:(Symbol,Option[P5]),s6:(Symbol,Option[P6]),s7:(Symbol,Option[P7])) = Select7(s1,s2,s3,s4,s5,s6,s7)
		def apply[P1,P2,P3,P4,P5,P6,P7,P8](s1:(Symbol,Option[P1]),s2:(Symbol,Option[P2]),s3:(Symbol,Option[P3]),s4:(Symbol,Option[P4]),s5:(Symbol,Option[P5]),s6:(Symbol,Option[P6]),s7:(Symbol,Option[P7]),s8:(Symbol,Option[P8])) = Select8(s1,s2,s3,s4,s5,s6,s7,s8)
		def apply[P1,P2,P3,P4,P5,P6,P7,P8,P9](s1:(Symbol,Option[P1]),s2:(Symbol,Option[P2]),s3:(Symbol,Option[P3]),s4:(Symbol,Option[P4]),s5:(Symbol,Option[P5]),s6:(Symbol,Option[P6]),s7:(Symbol,Option[P7]),s8:(Symbol,Option[P8]),s9:(Symbol,Option[P9])) = Select9(s1,s2,s3,s4,s5,s6,s7,s8,s9)
		def apply[P1,P2,P3,P4,P5,P6,P7,P8,P9,P10](s1:(Symbol,Option[P1]),s2:(Symbol,Option[P2]),s3:(Symbol,Option[P3]),s4:(Symbol,Option[P4]),s5:(Symbol,Option[P5]),s6:(Symbol,Option[P6]),s7:(Symbol,Option[P7]),s8:(Symbol,Option[P8]),s9:(Symbol,Option[P9]),s10:(Symbol,Option[P10])) = Select10(s1,s2,s3,s4,s5,s6,s7,s8,s9,s10)
		def apply[P1,P2,P3,P4,P5,P6,P7,P8,P9,P10,P11,P12,P13](s1:(Symbol,Option[P1]),s2:(Symbol,Option[P2]),s3:(Symbol,Option[P3]),s4:(Symbol,Option[P4]),s5:(Symbol,Option[P5]),s6:(Symbol,Option[P6]),s7:(Symbol,Option[P7]),s8:(Symbol,Option[P8]),s9:(Symbol,Option[P9]),s10:(Symbol,Option[P10]),s11:(Symbol,Option[P11]),s12:(Symbol,Option[P12]),s13:(Symbol,Option[P13])) = Select13(s1,s2,s3,s4,s5,s6,s7,s8,s9,s10,s11,s12,s13)
	}
	object SelectList {
		def apply(l:Option[List[Any]]) = new Select(){
			def build[R](b:(Any)=>Option[R]):Option[List[R]] = l.map{ _.flatMap{ p => b(p) }.toList }
		}
	}
	sealed abstract class Select {
		def ifDefined[T](o:(Symbol,Option[_])*)(e: =>T):Option[T] = o.filter{ _._2.isEmpty }.map{ _._1 }.toList match {
			case Nil => Some(e)
			case err:List[Symbol] =>
				logger.warn(s"not exist: ${err.mkString(", ")}")
				None
		}
	}
	case class Select1[P1](p1:(Symbol,Option[P1])) extends Select {
		def build[T](b:(P1)=>T):Option[T] = ifDefined(p1){ b(p1._2.get) }
	}
	case class Select2[P1,P2](p1:(Symbol,Option[P1]),p2:(Symbol,Option[P2])) extends Select {
		def build[T](b:(P1,P2)=>T):Option[T] = ifDefined(p1,p2){ b(p1._2.get, p2._2.get) }
	}
	case class Select3[P1,P2,P3](p1:(Symbol,Option[P1]),p2:(Symbol,Option[P2]),p3:(Symbol,Option[P3])) extends Select {
		def build[T](b:(P1,P2,P3)=>T):Option[T] = ifDefined(p1,p2,p3){ b(p1._2.get, p2._2.get, p3._2.get) }
	}
	case class Select4[P1,P2,P3,P4](p1:(Symbol,Option[P1]),p2:(Symbol,Option[P2]),p3:(Symbol,Option[P3]),p4:(Symbol,Option[P4])) extends Select {
		def build[T](b:(P1,P2,P3,P4)=>T):Option[T] = ifDefined(p1,p2,p3,p4){ b(p1._2.get, p2._2.get, p3._2.get, p4._2.get) }
	}
	case class Select5[P1,P2,P3,P4,P5](p1:(Symbol,Option[P1]),p2:(Symbol,Option[P2]),p3:(Symbol,Option[P3]),p4:(Symbol,Option[P4]),p5:(Symbol,Option[P5])) extends Select {
		def build[T](b:(P1,P2,P3,P4,P5)=>T):Option[T] = ifDefined(p1,p2,p3,p4,p5){ b(p1._2.get, p2._2.get, p3._2.get, p4._2.get, p5._2.get) }
	}
	case class Select6[P1,P2,P3,P4,P5,P6](p1:(Symbol,Option[P1]),p2:(Symbol,Option[P2]),p3:(Symbol,Option[P3]),p4:(Symbol,Option[P4]),p5:(Symbol,Option[P5]),p6:(Symbol,Option[P6])) extends Select {
		def build[T](b:(P1,P2,P3,P4,P5,P6)=>T):Option[T] = ifDefined(p1,p2,p3,p4,p5,p6){ b(p1._2.get, p2._2.get, p3._2.get, p4._2.get, p5._2.get, p6._2.get) }
	}
	case class Select7[P1,P2,P3,P4,P5,P6,P7](p1:(Symbol,Option[P1]),p2:(Symbol,Option[P2]),p3:(Symbol,Option[P3]),p4:(Symbol,Option[P4]),p5:(Symbol,Option[P5]),p6:(Symbol,Option[P6]),p7:(Symbol,Option[P7])) extends Select {
		def build[T](b:(P1,P2,P3,P4,P5,P6,P7)=>T):Option[T] = ifDefined(p1,p2,p3,p4,p5,p6,p7){ b(p1._2.get, p2._2.get, p3._2.get, p4._2.get, p5._2.get, p6._2.get, p7._2.get) }
	}
	case class Select8[P1,P2,P3,P4,P5,P6,P7,P8](p1:(Symbol,Option[P1]),p2:(Symbol,Option[P2]),p3:(Symbol,Option[P3]),p4:(Symbol,Option[P4]),p5:(Symbol,Option[P5]),p6:(Symbol,Option[P6]),p7:(Symbol,Option[P7]),p8:(Symbol,Option[P8])) extends Select {
		def build[T](b:(P1,P2,P3,P4,P5,P6,P7,P8)=>T):Option[T] = ifDefined(p1,p2,p3,p4,p5,p6,p7,p8){ b(p1._2.get, p2._2.get, p3._2.get, p4._2.get, p5._2.get, p6._2.get, p7._2.get, p8._2.get) }
	}
	case class Select9[P1,P2,P3,P4,P5,P6,P7,P8,P9](p1:(Symbol,Option[P1]),p2:(Symbol,Option[P2]),p3:(Symbol,Option[P3]),p4:(Symbol,Option[P4]),p5:(Symbol,Option[P5]),p6:(Symbol,Option[P6]),p7:(Symbol,Option[P7]),p8:(Symbol,Option[P8]),p9:(Symbol,Option[P9])) extends Select {
		def build[T](b:(P1,P2,P3,P4,P5,P6,P7,P8,P9)=>T):Option[T] = ifDefined(p1,p2,p3,p4,p5,p6,p7,p8,p9){ b(p1._2.get, p2._2.get, p3._2.get, p4._2.get, p5._2.get, p6._2.get, p7._2.get, p8._2.get, p9._2.get) }
	}
	case class Select10[P1,P2,P3,P4,P5,P6,P7,P8,P9,P10](p1:(Symbol,Option[P1]),p2:(Symbol,Option[P2]),p3:(Symbol,Option[P3]),p4:(Symbol,Option[P4]),p5:(Symbol,Option[P5]),p6:(Symbol,Option[P6]),p7:(Symbol,Option[P7]),p8:(Symbol,Option[P8]),p9:(Symbol,Option[P9]),p10:(Symbol,Option[P10])) extends Select {
		def build[T](b:(P1,P2,P3,P4,P5,P6,P7,P8,P9,P10)=>T):Option[T] = ifDefined(p1,p2,p3,p4,p5,p6,p7,p8,p9,p10){ b(p1._2.get, p2._2.get, p3._2.get, p4._2.get, p5._2.get, p6._2.get, p7._2.get, p8._2.get, p9._2.get, p10._2.get) }
	}
	case class Select13[P1,P2,P3,P4,P5,P6,P7,P8,P9,P10,P11,P12,P13](p1:(Symbol,Option[P1]),p2:(Symbol,Option[P2]),p3:(Symbol,Option[P3]),p4:(Symbol,Option[P4]),p5:(Symbol,Option[P5]),p6:(Symbol,Option[P6]),p7:(Symbol,Option[P7]),p8:(Symbol,Option[P8]),p9:(Symbol,Option[P9]),p10:(Symbol,Option[P10]),p11:(Symbol,Option[P11]),p12:(Symbol,Option[P12]),p13:(Symbol,Option[P13])) extends Select {
		def build[T](b:(P1,P2,P3,P4,P5,P6,P7,P8,P9,P10,P11,P12,P13)=>T):Option[T] = ifDefined(p1,p2,p3,p4,p5,p6,p7,p8,p9,p10){ b(p1._2.get, p2._2.get, p3._2.get, p4._2.get, p5._2.get, p6._2.get, p7._2.get, p8._2.get, p9._2.get, p10._2.get, p11._2.get, p12._2.get, p13._2.get) }
	}
}

case class Path(selector:Any*){
	private[this] lazy val logger = LoggerFactory.getLogger(classOf[Path])
	def getList(value:Any):Option[List[Any]] = select(value, 0) match {
		case Some(l:List[_]) => Some(l)
		case _ => None
	}
	def getMap(value:Any):Option[Map[_,Any]] = select(value, 0) match {
		case Some(l:Map[_,_]) => Some(l)
		case _ => None
	}
	def getString(value:Any):Option[String] = select(value, 0).map{ _.toString }
	def getDouble(value:Any):Option[Double] = select(value, 0) match {
		case Some(value:String) => Try{ value.toDouble }.toOption
		case Some(value:Double) => Some(value)
		case Some(value:Int) => Some(value.toDouble)
		case unknown =>
			logger.warn(s"unknown double value: $unknown on ${selector.mkString("/")} in ${value.toJSON}")
			None
	}
	def getInt(value:Any):Option[Int] = select(value, 0) match {
		case Some(value:String) => Try{ value.toInt }.toOption
		case Some(value:Double) => Some(value.toInt)
		case Some(value:Int) => Some(value)
		case unknown =>
			logger.warn(s"unknown int value: $unknown on ${selector.mkString("/")} in ${value.toJSON}")
			None
	}
	private[this] def select(value:Any, i:Int):Option[Any] = if(i == selector.size){
		Some(value)
	} else value match {
		case array:List[_] =>
			selector(i) match {
				case j:Int if j<array.length => select(array(j), i+1)
				case _ => None
			}
		case map:Map[_,_] =>
			map.asInstanceOf[Map[Any,Any]].get(selector(i)).flatMap{ s =>select(s, i+1) }
		case _ => None
	}
}