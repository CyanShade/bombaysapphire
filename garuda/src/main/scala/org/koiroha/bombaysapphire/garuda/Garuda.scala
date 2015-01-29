/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire.garuda

import java.io.File
import java.sql.Timestamp
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{Executors, ThreadFactory}

import org.json4s._
import org.json4s.native.JsonMethods._
import org.koiroha.bombaysapphire.GarudaAPI
import org.koiroha.bombaysapphire.garuda.entities._
import org.koiroha.bombaysapphire.garuda.schema.Tables
import org.koiroha.bombaysapphire.geom.{LatLng, Polygon, Rectangle, Region}
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.slick.driver.PostgresDriver
import scala.slick.driver.PostgresDriver.simple._
import scala.slick.jdbc.StaticQuery.interpolation

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Garuda
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * 情報収集システム。
 *
 * @author Takami Torao
 */
class Garuda(context:Context) extends GarudaAPI {
	import org.koiroha.bombaysapphire.garuda.Garuda._

	private[this] implicit val formats = DefaultFormats
	private[this] implicit val _context = context

	private[this] implicit val threads = ExecutionContext.fromExecutor(Executors.newCachedThreadPool(new ThreadFactory {
		private[this] val i = new AtomicInteger(0)
		override def newThread(r: Runnable): Thread = {
			new Thread(r, s"GarudaWorker-${i.getAndIncrement}")
		}
	}))

	/**
	 * 開発時は容量が逼迫しているためログは保存しない。
	 */
	private[this] val saveLog = false

	/** 指定領域を含む tile key の参照 */
	override def tileKeys(rect:Rectangle):Future[Seq[(String, Rectangle)]] = future { implicit session =>
		logger.trace(s"tileKey($rect)")
		Tables.Portals.groupBy{ _.tileKey }
			.map{ case (tileKey, c) => (tileKey, c.map{_.late6}.max, c.map{_.late6}.min, c.map{_.lnge6}.max, c.map{_.lnge6}.min) }
			.list
			.map { case (tileKey, lat0, lat1, lng0, lng1) =>
				(tileKey, Rectangle(lat0.get/1e6, lng0.get/1e6, lat1.get/1e6, lng1.get/1e6))
			}
	}

	/** 行政区指定の巡回に対する領域の問い合わせ */
	override def administrativeDistricts():Future[Seq[(String, String, String)]] = future { implicit session =>
		logger.trace(s"administrativeDistricts()")
		Tables.HeuristicRegions
			.filter{ _.side === "O" }
			.sortBy{ c => (c.country, c.state, c.city) }
			.map{ c => (c.country, c.state.getOrElse(""), c.city.getOrElse("")) }
			.list
	}

	/** 行政区指定の巡回に対する領域の問い合わせ */
	override def administrativeDistrict(country:String, state:String, city:String):Future[Option[Region]] = future { implicit s =>
		logger.trace(s"administrativeDistrict($country,$state,$city)")
		val polygons = (if(state == "") {
			Tables.HeuristicRegions.filter{ t => t.side === "O" && t.country === country}
		} else if(city == ""){
			Tables.HeuristicRegions.filter{ t => t.side === "O" && t.country === country && t.state === state }
		} else {
			Tables.HeuristicRegions.filter{ t => t.side === "O" && t.country === country && t.state === state && t.city === city }
		}).map{ t => t.region }.list.map { region =>
			if(region.startsWith("((") && region.endsWith("))")){
				Polygon(region.substring(2, region.length - 2).split("\\),\\(")
					.map{ _.split(",", 2).map{ _.toDouble } }.map{ case Array(lat,lng) => LatLng(lat,lng) }.toSeq)
			} else throw new IllegalArgumentException(s"unexpected polygon data format: $region")
		}
		if(polygons.isEmpty){
			None
		} else {
			Some(Region(s"$country/$state/$city", polygons))
		}
	}

	/** データの保存 */
	override def store(method:String, request:String, response:String, timestamp:Long):Unit = future { implicit session =>
		if(logger.isTraceEnabled){
			logger.trace(s"store($method,req,res,$timestamp)")
			parseOpt(request).map{ j => pretty(render(j)) }.foreach{ r =>
				logger.trace(s"  req: ${trim(r,16*1024)}")
			}
			parseOpt(response).map{ j => pretty(render(j)) }.foreach{ r =>
				logger.trace(s"  res: ${trim(r,16*1024)}")
			}
		}
		val tm = new Timestamp(timestamp)
		// デバッグや分析に不要な大量のゴミ情報を除去
		def _parse(s:String) = parse(s).transformField{
			case ("v",v) => "v" -> JNull
			case ("b",b) => "b" -> JNull
			case ("c",c) => "c" -> JNull
		}
		lazy val reqJson = _parse(request)
		val resJson = _parse(response)
		if(resJson.children.size == 0){
			logger.error(s"empty response: $method=$request")
		} else (method match {
			case "getGameScore" => GameScore(resJson \ "result")
			case "getRegionScoreDetails" => RegionScoreDetails(resJson)
			case "getPlexts" => Plext(resJson).map{ plext =>
				plext.save(tm)
				plext
			}
			case "getEntities" => Entities(resJson).map{ entities =>
				entities.save(tm)
				entities
			}
			case "getPortalDetails" => PortalDetails(resJson).map { pd =>
				savePortalDetail(tm, pd, request)
				pd
			}
			case _ =>
				logger.warn(s"unexpected method: $method; $request")
				resJson.asInstanceOf[JObject].values.get("result")
		}) foreach { obj =>
			val str = obj.toString
			logger.trace(s"${if(str.length>5000) str.substring(0,5000) else str}")
		}
	}

	/**
	 * 取得したポータル詳細をデータベースに保存。
	 * @param tm 取得日時
	 */
	private[this] def savePortalDetail(tm:Timestamp, pd:PortalDetails, req:String)(implicit session:Session):Unit = {
		// レスポンスの PortalDetails には guid が含まれないためリクエスト中から guid を取得する
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

	private[this] def getContentAsJSON(httpMessage:String):JValue = httpMessage.split("\n\n", 2) match {
		case Array(_) => JNull
		case Array(_,c) => parse(c)
	}

	private[this] def future[T](exec:(PostgresDriver.backend.Session)=>T):Future[T] = Future {
		context.database.withSession { exec }
	}

	private[this] def trim(s:String, len:Int):String = if(s.length <= len) s else {
		s.substring(0, len - 1) + "…"
	}
}

object Garuda extends App {
	private[Garuda] val logger = LoggerFactory.getLogger(classOf[Garuda])

	val context = Context(new File(args.head))
	context.server.start()
}
