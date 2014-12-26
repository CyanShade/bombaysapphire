/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire

import org.json4s._
import org.json4s.native.JsonMethods._
import org.koiroha.bombaysapphire.Entities._
import org.koiroha.bombaysapphire.Implicit._
import org.koiroha.bombaysapphire.PortalDetails.{Mod, Resonator}
import org.koiroha.bombaysapphire.RegionScoreDetails._
import org.slf4j.LoggerFactory

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
	private[this] implicit val formats = DefaultFormats
	def apply(value:JValue):Option[GameScore] = value transformOpt Some(GameScore(value(0).toInt, value(1).toInt))
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
	regionVertices:List[GameScore]
)
object RegionScoreDetails {
	private[this] val logger = LoggerFactory.getLogger(classOf[RegionScoreDetails])
	private[this] implicit val formats = DefaultFormats
	case class ScoreHistory(checkpoint:Int, enlightened:Int, resistance:Int)
	object ScoreHistory {
		def apply(value:JValue):ScoreHistory = value transform {
			ScoreHistory(value(0).toInt, value(1).toInt, value(2).toInt)
		}
	}
	case class TopAgent(nick:String, team:Team)
	object TopAgent {
		def apply(value:JValue):TopAgent = value transform {
			TopAgent((value \ "nick").extract[String], Team((value \ "team").extract[String]).get)
		}
	}

	def apply(value:JValue):Option[RegionScoreDetails] = value transformOpt {
		val gameScore = GameScore(value \ "result" \ "gameScore")
		val scoreHistory = (value \ "result" \ "scoreHistory").toList.map{ x => ScoreHistory(x:JValue) }
		val topAgents = (value \ "result" \ "topAgents").toList.map{ x => TopAgent(x:JValue) }
		val timeToEndOfBaseCycleMs = (value \ "result" \ "timeToEndOfBaseCycleMs").extractOpt[Double]
		val regionName = (value \ "result" \ "regionName").extractOpt[String]
		val regionVertices = (value \ "result" \ "regionVertices").toList.flatMap{ x => GameScore(x:JValue) }
		Some(RegionScoreDetails(gameScore.get, scoreHistory, topAgents, timeToEndOfBaseCycleMs.get, regionName.get, regionVertices))
	}
}

case class Entities(map:Map[String,MapRegion])
object Entities {
	private[this] val logger = LoggerFactory.getLogger(classOf[Entities])
	private[this] implicit val formats = DefaultFormats

	case class MapRegion(deletedGameEntityGuids:Seq[String], gameEntities:Seq[GameEntity], error:Option[String])
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
	case class Portal(guid:String, unknown:Double, latE6:Int, health:Int, image:String, resCount:Int, lngE6:Int, team:Team, title:String, ornaments:Seq[String], level:Int) extends GameEntity(guid, unknown, "portal")
	object Portal {
		private[this] val logger = LoggerFactory.getLogger(classOf[Portal])
		def apply(guid:String, unknown:Double, value:JValue):Portal = value transform {
			val latE6 = (value(2) \ "latE6").toInt
			val health = (value(2) \ "health").toInt
			val image = (value(2) \ "image").extractOpt[String].get
			val resCount = (value(2) \ "resCount").toInt
			val lngE6 = (value(2) \ "lngE6").toInt
			val team = (value(2) \ "team").extractOpt[String].flatMap{ Team.apply }.get
			val title = (value(2) \ "title").extractOpt[String].get
			val ornaments = (value(2) \ "ornaments").toList.map{ _.extractOpt[String].get }
			val level = (value(2) \ "level").toInt
			Portal(guid, unknown, latE6, health, image, resCount, lngE6, team, title, ornaments, level)
		}
	}
	case class Region(guid:String, unknown:Double, points:Seq[Point], team:Team) extends GameEntity(guid, unknown, "region")
	object Region {
		def apply(guid:String, unknown:Double, value:JValue):Region = value transform {
			val points = (value(2) \ "points").toList.map{ x => Point(x:JValue) }
			val team = (value(2) \ "team").extractOpt[String].flatMap{ Team.apply }.get
			Region(guid, unknown, points, team)
		}
	}
	case class Edge(guid:String, unknown:Double, dest:Point, org:Point, team:Team) extends GameEntity(guid, unknown, "edge")
	object Edge {
		def apply(guid:String, unknown:Double, value:JValue):Edge = value transform {
			val dest = Point(value.transformField{
				case ("dGuid",v) => ("guid",v)
				case ("dLatE6",v) => ("latE6",v)
				case ("dLngE6",v) => ("lngE6",v)
			})
			val org = Point(value.transformField{
				case ("oGuid",v) => ("guid",v)
				case ("oLatE6",v) => ("latE6",v)
				case ("oLngE6",v) => ("lngE6",v)
			})
			val team = (value(2) \ "team").extractOpt[String].flatMap{ Team.apply }.get
			Edge(guid, unknown, dest, org, team)
		}
	}
	case class Point(guid:String, latE6:Int, lngE6:Int)
	object Point {
		def apply(value:JValue):Point = value transform {
			val guid = (value \ "guid").extractOpt[String].get
			val latE6 = (value \ "latE6").toInt
			val lngE6 = (value \ "lngE6").toInt
			Point(guid, latE6, lngE6)
		}
	}

	def apply(value:JValue):Option[Entities] = value transformOpt {
		val region = (value \ "result" \ "map").toMap.map{ case (regionId, regionInfo) =>
			(regionId, (regionInfo \ "error").extractOpt[String] match {
				case Some(error) =>
					MapRegion(Nil, Nil, Some(error))
				case None =>
					val deletedGameEntityGuids = (regionInfo \ "deletedGameEntityGuids").toList.flatMap{ _.extractOpt[String] }
					val gameEntities = (regionInfo \ "gameEntities").toList.flatMap{ x =>
						val guid = x(0).extractOpt[String].get
						val unknown = x(1).extractOpt[Double].get
						(x \ "type").extractOpt[String].get match {
							case "portal" => Some(Portal(guid, unknown, x))
							case "edge" => Some(Edge(guid, unknown, x))
							case "region" => Some(Region(guid, unknown, x))
							case uk =>
								logger.warn(s"unknown game-entity type: $uk")
								None
						}
					}
					MapRegion(deletedGameEntityGuids, gameEntities)
			})
		}
		if(region.size == 0) None else Some(Entities(region))
	}
}

case class Plext(guid:String, unknown:Double, categories:Int, markup:String, plextType:String, team:Team, text:String)
object Plext {
	private[this] val logger = LoggerFactory.getLogger(classOf[Plext])
	private[this] implicit val formats = DefaultFormats
	def apply(value:JValue):Option[Seq[Plext]] = value transformOpt {
		Some((value \ "success").toList.map { x =>
			val guid = x(0).extractOpt[String].get
			val unknown = x(1).extractOpt[Double].get
			val categories = (x(2) \ "plext" \ "categories").toInt
			val markup = compact(render(x(2) \ "plext" \ "markup"))
			val plextType = (x(2) \ "plext" \ "plextType").extractOpt[String].get
			val team = (x(2) \ "plext" \ "team").extractOpt[String].flatMap{ Team.apply }.get
			val text = (x(2) \ "plext" \ "text").extractOpt[String].get
			Plext(guid, unknown, categories, markup, plextType, team, text)
		})
	}
}

	/*
--- getPortalDetails ---
{"latE6":3.5697816E7,"health":99.0,"resonators":[{"owner":"materia64","energy":6000.0,"level":8.0},{"owner":"takayuki8695","energy":5993.0,"level":8.0},{"owner":"Ziraiya","energy":5996.0,"level":8.0},{"owner":"banban21","energy":6000.0,"level":8.0},{"owner":"kosuke01","energy":5991.0,"level":8.0},{"owner":"xanadu2000","energy":5986.0,"level":8.0},{"owner":"st39pq28","energy":5951.0,"level":8.0},{"owner":"colsosun","energy":5984.0,"level":8.0}],"image":"http://lh5.ggpht.com/s_rUFvp3UQoEHzPOLHgvkBZqU0ANBOTCwhbYhZHuxdy4xwoENZoFCGN2Cd8WRHMFcARF_K95hs2gUhZnkeg","mods":[{"owner":"xanadu2000","stats":{"REMOVAL_STICKINESS":"70","MITIGATION":"70"},"name":"AXA Shield","rarity":"VERY_RARE"},{"owner":"Ziraiya","stats":{"REMOVAL_STICKINESS":"20","MITIGATION":"30"},"name":"Portal Shield","rarity":"COMMON"},{"owner":"HedgehogPonta","stats":{"REMOVAL_STICKINESS":"30","MITIGATION":"40"},"name":"Portal Shield","rarity":"RARE"},{"owner":"HedgehogPonta","stats":{"REMOVAL_STICKINESS":"30","MITIGATION":"40"},"name":"Portal Shield","rarity":"RARE"}],"resCount":8.0,"lngE6":1.39812408E8,"team":"RESISTANCE","owner":"Ziraiya","title":"美容ポコ","type":"portal","ornaments":[],"level":8.0}
*/
case class PortalDetails(health:Int, image:String, latE6:Int, level:Int, lngE6:Int, mods:Seq[Option[Mod]], ornaments:Seq[String], owner:String, resCount:Int, resonators:Seq[Resonator], team:Team, title:String, `type`:String)
object PortalDetails {
	private[this] implicit val formats = DefaultFormats
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
	def apply(value:JValue):Option[PortalDetails] = value transformOpt {
		val health = (value \ "health").toInt
		val image = (value \ "image").extractOpt[String].get
		val latE6 = (value \ "latE6").toInt
		val level = (value \ "level").toInt
		val lngE6 = (value \ "lngE6").toInt
		val mods = (value \ "mods").toList.map{ e =>
			if(e == JNull) None else e.transformOpt {
				val name = (e \ "name").extractOpt[String].get
				val owner = (e \ "owner").extractOpt[String].get
				val rarity = (e \ "rarity").extractOpt[String].get
				val stats = (e \ "stats").toMap.map{ case (k,v) => k -> v.toString }.toMap
				Some(Mod(name, owner, rarity, stats))
			}
		}
		val ornaments = (value \ "ornaments").toList.flatMap{ _.extractOpt[String] }
		val owner = (value \ "owner").extractOpt[String].get
		val resCount = (value \ "resCount").toInt
		val resonators = (value \ "resonators").toList.map{ r =>
			val energy = (r \ "energy").toInt
			val level = (r \ "level").toInt
			val owner = (r \ "owner").extractOpt[String].get
			Resonator(energy, level, owner)
		}
		val team = (value \ "team").extractOpt[String].flatMap{ Team.apply }.get
		val title = (value \ "title").extractOpt[String].get
		val `type` = (value \ "type").extractOpt[String].get
		Some(PortalDetails(health, image, latE6, level, lngE6, mods, ornaments, owner, resCount, resonators, team, title, `type`))
	}
}


object Implicit {
	private[this] val logger = LoggerFactory.getLogger(classOf[Entities])
	private[this] class Checked() extends Exception
	implicit class _JValue(value:JValue) {
		def transformOpt[T](f: =>Option[T]):Option[T] = try {
			f
		} catch {
			case _:Checked => None
			case ex:Exception =>
				logger.warn(s"parse error: ${pretty(render(value))}", ex)
				None
		}
		def transform[T](f: =>T):T = try {
			f
		} catch {
			case ex:Exception =>
				if(! ex.isInstanceOf[Checked]){
					logger.warn(s"parse error: ${pretty(render(value))}", ex)
				}
				throw new Checked
		}
		def toInt:Int = value match {
			case i:JInt => i.num.toInt
			case i:JDouble => i.num.toInt
			case i:JDecimal => i.num.toInt
			case i:JString => i.s.toInt
			case unknown => throw new NumberFormatException(unknown.toString)
		}
		def toList:List[JValue] = value match {
			case a:JArray => a.arr
			case _ => Nil
		}
		def toMap:Map[String,JValue] = value match {
			case o:JObject => o.obj.toMap
			case _ => Map()
		}
	}
}
