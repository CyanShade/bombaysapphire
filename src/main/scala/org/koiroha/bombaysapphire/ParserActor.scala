/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire

import akka.actor.{Actor, ActorLogging}
import org.json4s.JsonAST.{JNull, JObject}
import org.json4s.native.JsonMethods._
import org.slf4j.LoggerFactory

import scala.slick.driver.PostgresDriver.simple._
import scala.slick.jdbc.StaticQuery.interpolation

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// ParserActor
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class ParserActor extends Actor with ActorLogging {
	private[ParserActor] val logger = LoggerFactory.getLogger(classOf[ParserActor])
	val db = Database.forURL("jdbc:postgresql://localhost:5433/bombaysapphire", user="postgres", password="postgres", driver="org.postgresql.Driver")

	def receive = {
		case ParseTask(method, content, request, response, realtime) =>
			db.withSession { implicit session =>
				if(realtime){
					sqlu"insert into intel.logs(method,content,request,response) values($method,$content::jsonb,$request,$response)".first.run
				}

				val json = parse(content).transformField{
					case ("b",_) => "b" -> JNull
					case ("c",_) => "c" -> JNull
				}
				(method match {
					case "getGameScore" => GameScore(json \ "result")
					case "getRegionScoreDetails" => RegionScoreDetails(json)
					case "getPlexts" => Plext(json)
					case "getEntities" => Entities(json)
					case "getPortalDetails" => PortalDetails(json)
					case _ => json.asInstanceOf[JObject].values.get("result")
				}) match {
					case Some(obj) =>
						val str = obj.toString
						logger.debug(s"${if(str.length>5000) str.substring(0,5000) else str}")
					case None =>
						None//logger.warn(content)
				}

				/*
				JSON.parseFull(content) match {
					case Some(_value:Map[_,_]) =>
						// デバッグや分析に不要な大量のゴミ情報を除去
						val value = _value.filter{ case (k,_) => k != "b" && k != "c" }
						(method match {
							case "getGameScore" => GameScore(value)
							case "getRegionScoreDetails" => RegionScoreDetails(value)
							case "getPlexts" => Plext(value)
							case "getEntities" => Entities(value)
							case "getPortalDetails" => PortalDetails(value)
							case _ => value.asInstanceOf[Map[String,Any]].get("result")
						}) match {
							case Some(obj) =>
								val str = obj.toString
								logger.debug(s"${if(str.length>5000) str.substring(0,5000) else str}")
							case None =>
								logger.warn(content)
						}
					case None =>
						logger.debug(s"parse error: ${content.substring(0, 1000)}...")
				}
				*/
			}
	}
}

case class ParseTask(method:String, content:String, request:String, response:String, realtime:Boolean)
