/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire

import org.koiroha.bombaysapphire.Entities._
import org.koiroha.bombaysapphire.RegionScoreDetails.Entities.MapEntity
import org.koiroha.bombaysapphire.RegionScoreDetails._

import scala.util.Try

sealed abstract class Team
object Team {
	def apply(name:String):Team = name.toLowerCase match {
		case "enlightened" => Enlightened
		case "resistance" => Resistance
	}
}
case object Resistance extends Team
case object Enlightened extends Team

case class RegionScoreDetails(
	gameScore:GameScore,
	scoreHistory:List[ScoreHistory],
	topAgents:List[TopAgent],
	timeToEndOfBaseCycleMs:Double,
	regionName:String,
	regionVertices:List[RegionVertex]
) {
	/*
	{
		gameScore: [2695797, 6015489],
		scoreHistory: [ [18, 2428288, 5722443], ..., [1, 2821766, 6107432] ],
		topAgents: [{ nick:golco30, team:RESISTANCE}, ...],
		timeToEndOfBaseCycleMs: 2791045.0,
		regionName: "PA01-ALPHA-12",
		regionVertices: [ [3.5769632E7, 1.38719142E8], ... ]
	}
	*/
}
object RegionScoreDetails {
	case class GameScore(enlightened:Int, resistance:Int)
	case class ScoreHistory(checkpoint:Int, enlightened:Int, resistance:Int)
	case class TopAgent(nick:String, team:Team)
	case class RegionVertex(enlightened:Double, resistance:Double)

	def apply(value:Map[String, _]):Option[RegionScoreDetails] = try {
		val gameScore = value.get("gameScore").map{ case List(e:Double, r:Double) => GameScore(e.toInt, r.toInt) }.get
		val scoreHistory = value.get("scoreHistory").map{ case hist:List[List[Double]] => hist.map{ case List(c, e, r) => ScoreHistory(c.toInt, e.toInt, r.toInt) }.sortBy{ _.checkpoint } }.get
		val topAgents = value.get("topAgents").map{ case as:List[Map[String,String]] => as.map{ m => TopAgent(m("nick"), Team(m("team")))} }.get
		val timeToEndOfBaseCycleMs = value("timeToEndOfBaseCycleMs").asInstanceOf[Double]
		val regionName = value("regionName").toString
		val regionVertices = value.get("regionVertices").map{ case rv:List[List[Double]] => rv.map{ case List(e, r) => RegionVertex(e, r) } }.get
		Some(RegionScoreDetails(gameScore, scoreHistory, topAgents, timeToEndOfBaseCycleMs, regionName, regionVertices))
	} catch {
		case ex:Exception =>
			None
	}
}

case class Entities(map:Map[String,MapEntity]){

}
object Entities {
	case class MapEntity(
		deletedGameEntityGuids:Seq[String],
		gameEntities:
	)

	case class GameEntity(

		                     ){
		/*
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
	}
}