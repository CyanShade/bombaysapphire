/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire

import java.sql.Timestamp

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import ch.hsr.geohash.GeoHash
import com.typesafe.config.ConfigFactory
import org.json4s.JsonAST.JNull
import org.json4s.native.JsonMethods._
import org.json4s.{DefaultFormats, JObject, JValue}
import org.koiroha.bombaysapphire.Entities.Portal
import org.koiroha.bombaysapphire.schema.Tables
import org.slf4j.LoggerFactory

import scala.slick.driver.PostgresDriver.simple._
import scala.slick.jdbc.StaticQuery.interpolation

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// ParserActor
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class ParserActor extends Actor with ActorLogging with DBAccess {
	private[ParserActor] val logger = LoggerFactory.getLogger(classOf[ParserActor])
	private[this] implicit val formats = DefaultFormats

	/**
	 * 開発時は容量が逼迫しているためログは保存しない。
	 */
	private[this] val saveLog = false

	def receive = {
		case ParseTask(method, logid, runAt) =>
			val tm = new Timestamp(runAt)

			db.withSession { implicit session =>
				// 共有ストレージ経由で Blob データを取得
				val (content,request,response) = Tables.Logs.filter { _.id === logid }.map{ l => (l.content,l.request,l.response) }.first
				if(! saveLog){
					Tables.Logs.filter{ _.id === logid }.delete
				}

				// デバッグや分析に不要な大量のゴミ情報を除去
				val json = parse(content).transformField{
					case ("v",v) =>
						logger.debug(s"v: ${v.extract[String]}")
						"v" -> JNull
					case ("b",b) =>
						logger.debug(s"b: ${b.extract[String]}")
						"b" -> JNull
					case ("c",c) =>
						logger.debug(s"c: ${compact(render(c))}")
						"c" -> JNull
				}
				(method match {
					case "getGameScore" => GameScore(json \ "result")
					case "getRegionScoreDetails" => RegionScoreDetails(json)
					case "getPlexts" => Plext(json)
					case "getEntities" => Entities(json).map{ entities =>
						saveEntities(tm, entities)
						entities
					}
					case "getPortalDetails" => PortalDetails(json).map { pd =>
						savePortalDetail(tm, pd, request)
						pd
					}
					case _ =>
						logger.warn(s"unexpected method: $method; $content")
						json.asInstanceOf[JObject].values.get("result")
				}) foreach { obj =>
						val str = obj.toString
						logger.trace(s"${if(str.length>5000) str.substring(0,5000) else str}")
				}
			}
		case Shutdown() => context.stop(self)
	}

	/**
	 * 取得したエンティティをデータベースに保存。
	 * @param tm 取得日時
	 */
	private[this] def saveEntities(tm:Timestamp, entities:Entities)(implicit session:Session):Unit = {
		val start = System.currentTimeMillis()
		entities.map.foreach{ case (regionId, rm) =>
			// 削除されたポータルの検出と削除
			val avails = Tables.Portals.filter{ p => p.regionId === regionId && p.deletedAt.isEmpty }.map{ p => p.guid }.run
			avails.intersect(rm.deletedGameEntityGuids).foreach { guid =>
				val (id, latE6, lngE6, title, geohash) = Tables.Portals.filter{ p => p.guid === guid }
					.map { p => (p.id, p.late6, p.lnge6, p.title, p.nearlyGeohash)
				}.first
				val location = Tables.Geohash.filter { g => g.geohash === geohash}
						.map { g => (g.country, g.state, g.city)
				}.firstOption match {
					case Some((country, state, city)) => " ($city, $state, $country)"
					case None => ""
				}
				notice(latE6, lngE6,
					s"ポータルの消失を検出しました: $title$location", "portal_removed", id.toString)
				Tables.Portals.filter{ p => p.guid === guid && p.deletedAt.isEmpty }.map{ p => p.deletedAt }.update(Some(tm))
			}
			// 各種エンティティの保存
			rm.gameEntities.foreach {
				case cp:Portal => savePortal(tm, regionId, cp)
				case _ => None
			}
		}
		val time = System.currentTimeMillis() - start
		logger.debug(f"entities saved: ${entities.map.keys.mkString(",")}%s ($time%,d[ms])")
	}

	/**
	 * 取得したポータル詳細をデータベースに保存。
	 * @param tm 取得日時
	 */
	private[this] def savePortalDetail(tm:Timestamp, pd:PortalDetails, req:String)(implicit session:Session):Unit = {
		// レスポンスの PortalDetails には guid が含まれないためリクエスト中の guid を取得する
		// {"guid":"8cf1eb0d834f4f64898d9e683849d44e.16","v":"...","b":"...","c":"..."}
		(getContentAsJSON(req) \ "guid").extractOpt[String] match {
			case Some(guid) =>
				Tables.Portals.filter{ _.guid === guid }.map{ _.id }.firstOption match {
					case Some(id) =>
						sqlu"insert into intel.portal_state_logs(portal_id,owner,level,health,team,mitigation,resCount,resonators,mods) values($id,${pd.owner},${pd.level},${pd.health},${pd.team.symbol.toString},${0}},${pd.resCount},${pd.resonatorsJSON}::jonb,${pd.modsJSON}::jsonb)".first.run
					case None =>
						logger.debug(s"portal not exist for detail: $guid")
				}
			case None =>
				logger.warn(s"portal guid not exist in request: $req")
		}
	}

	/**
	 * 取得したポータルをデータベースに保存。
	 * @param tm 取得日時
	 */
	private[this] def savePortal(tm:Timestamp, regionId:String, cp:Portal)(implicit session:Session):Unit = {
		Tables.Portals.filter{ p => p.guid === cp.guid && p.deletedAt.isEmpty }.firstOption match {
			case Some(p) =>
				val record = Tables.Portals.filter{ x => x.id === p.id }
				// 画像 URL の変更
				if(p.image != cp.image){
					record.map{ p => (p.image,p.updatedAt) }.update((cp.image,tm))
				}
				// タイトルの変更
				if(p.title != cp.title){
					record.map{ p => (p.title,p.updatedAt) }.update((cp.title,tm))
				}
				// 位置の変更
				if(p.late6 != cp.latE6 || p.lnge6 != cp.lngE6){
					val geoHash = findGeoHash(cp.latE6, cp.lngE6)
					record.map{ x =>
						(x.late6,x.lnge6,x.nearlyGeohash,x.updatedAt)
					}.update((cp.latE6,cp.lngE6,geoHash.map{ _.geohash },tm))
				}
				// リージョンの変更
				if(p.regionId != regionId){
					record.map{ x => (x.regionId,x.updatedAt) }.update((regionId,tm))
				}
			case None =>
				val geoHash = findGeoHash(cp.latE6, cp.lngE6).map{ _.geohash }
				Tables.Portals.map{ x =>
					(x.guid, x.title, x.image, x.regionId, x.late6, x.lnge6, x.nearlyGeohash, x.createdAt, x.updatedAt)
				}.insert((cp.guid, cp.title, cp.image, regionId, cp.latE6, cp.lngE6, geoHash, tm, tm))
		}
	}

	private[this] def getContentAsJSON(httpMessage:String):JValue = httpMessage.split("\n\n", 2) match {
		case Array(_) => JNull
		case Array(_,c) => parse(c)
	}

	/**
	 * 指定された場所で何らかのイベントが発生した通知。
	 */
	private[this] def notice(latE6:Int, lngE6:Int, code:String, message:String, args:String*)(implicit session:Session):Unit = {
		logger.info(f"[${latE6/1e6}%.6f:${lngE6/1e6}%.6f] $message")
	}

	/**
	 * 緯度/経度に対して最も近い定義済み GeoHash を参照。
	 */
	private[this] def findGeoHash(latE6:Int, lngE6:Int)(implicit session:Session):Option[Tables.Geohash#TableElementType] = {
		val geoHash = GeoHash.withCharacterPrecision(latE6 / 1e6, lngE6 / 1e6, 5).toBase32
		Tables.Geohash.filter{ _.geohash === geoHash }.firstOption.orElse{
			val g = geoHash.substring(0, 4)
			Tables.Geohash.filter{ _.geohash like s"$g%" }.firstOption
		}
	}
}
object ParserActor extends App {
	val config = ConfigFactory.load("parser.conf")
	val system = ActorSystem.apply("bombaysapphire", config)
	val actor1 = system.actorOf(Props[ParserActor], "parser")
/*
	actor1 ! "Local"

	Thread.sleep(60000)
	system.shutdown()
*/
}
