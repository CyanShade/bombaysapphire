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
import org.koiroha.bombaysapphire.garuda.Entities.Portal
import org.koiroha.bombaysapphire.garuda.schema.Tables
import org.koiroha.bombaysapphire.geom.{LatLng, Polygon, Rectangle, Region}
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.slick.driver.PostgresDriver
import scala.slick.driver.PostgresDriver.simple._
import scala.slick.jdbc.StaticQuery.interpolation
import scala.util.{Failure, Success}

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
			case "getPlexts" => Plext(resJson)
			case "getEntities" => Entities(resJson).map{ entities =>
				saveEntities(tm, entities)
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

	// ==============================================================================================
	// エンティティの保存
	// ==============================================================================================
	/**
	 * 取得したエンティティをデータベースに保存。
	 * @param tm 取得日時
	 */
	private[this] def saveEntities(tm:Timestamp, entities:Entities)(implicit session:Session):Unit = {
		val start = System.currentTimeMillis()
		val portalReport = entities.map.filter{ case (tileKey,_) =>
			// zoom=17 の tile_key のみを保存対象とする
			tileKey.startsWith("17_")
		}.map{ case (tileKey, rm) =>
			// 削除されたポータルの検出と削除
			val avails = Tables.Portals.filter{ p => p.tileKey === tileKey && p.deletedAt.isEmpty }.map{ p => p.guid }.run
			avails.intersect(rm.deletedGameEntityGuids).foreach { guid =>
				val (id, latE6, lngE6, title, geohash, verifiedAt) = Tables.Portals.filter{ p => p.guid === guid }
					.map { p => (p.id, p.late6, p.lnge6, p.title, p.geohash, p.verifiedAt)
				}.first
				val location = Tables.Geohash.filter { g => g.geohash === geohash}
					.map { g => (g.country, g.state, g.city)
				}.firstOption match {
					case Some((country, state, city)) => " ($city, $state, $country)"
					case None => ""
				}
				notice(latE6, lngE6,
					s"ポータルの消失を検出しました: $title$location", "portal_removed", id.toString)
				Tables.Portals
					.filter{ p => p.guid === guid && p.deletedAt.isEmpty }
					.map{ p => (p.verifiedAt, p.deletedAt) }
					.update((tm, Some(tm)))
				savePortalEvent(id, "remove", verifiedAt, tm)
			}
			// 各種エンティティの保存
			tileKey -> rm.gameEntities.map {
				case cp:Portal =>
					if(savePortal(tm, tileKey, cp)) (1, 1) else (0, 1)
				case _ => (0, 0)
			}.reduceLeft{ (a, b) => (a._1+b._1, a._2+b._2) }
		}.toSeq.sortBy{ _._1 }
		val time = System.currentTimeMillis() - start
		portalReport.foreach{ case (tileKey, (newPortals, portals)) =>
			logger.debug(f"entities saved: $tileKey%s; total $portals%,d portals with $newPortals%,d new, $time%,d[ms]")
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

	/**
	 * 取得したポータルをデータベースに保存。
	 * @param tm 取得日時
	 * @return 新規に保存したとき true
	 */
	private[this] def savePortal(tm:Timestamp, regionId:String, cp:Portal)(implicit session:Session):Boolean = {
		Tables.Portals.filter{ p => p.guid === cp.guid && p.deletedAt.isEmpty }.firstOption match {
			case Some(p) =>
				val record = Tables.Portals.filter{ x => x.id === p.id }
				val verifiedAt = p.verifiedAt
				// 画像 URL の変更
				if(p.image != cp.image){
					record.map{ p => (p.image,p.updatedAt) }.update((cp.image,tm))
					savePortalEvent(p.id, "change_image", p.image, cp.image, verifiedAt, tm)
					logger.info(s"画像URLを変更しました: ${cp.guid}; ${cp.latE6}/${cp.lngE6}; ${cp.title}")
				}
				// タイトルの変更
				if(p.title != cp.title){
					record.map{ p => (p.title,p.updatedAt) }.update((cp.title,tm))
					savePortalEvent(p.id, "change_title", p.title, cp.title, verifiedAt, tm)
					logger.info(s"タイトルを変更しました: ${cp.guid}; ${cp.latE6}/${cp.lngE6}; ${cp.title}; ${p.title} -> ${cp.title}")
				}
				// 位置の変更
				if(p.late6 != cp.latE6 || p.lnge6 != cp.lngE6){
					record.map{ x =>
						(x.late6,x.lnge6,x.updatedAt)
					}.update((cp.latE6,cp.lngE6,tm))
					savePortalEvent(p.id, "change_location", s"${p.late6/1e6},${p.lnge6/1e6}", s"${cp.latE6/1e6},${cp.lngE6/1e6}", verifiedAt, tm)
					setGeoHashAsync(cp.guid, cp.latE6, cp.lngE6)
					logger.info(s"位置を変更しました: ${cp.guid}; ${cp.latE6}/${cp.lngE6}; ${cp.title}")
				}
				// タイルキーの変更
				if(p.tileKey != regionId){
					record.map{ x => (x.tileKey,x.updatedAt) }.update((regionId,tm))
					// logger.info(s"タイルキーを変更しました: ${cp.guid}; ${cp.latE6}/${cp.lngE6}; ${cp.title}; ${p.tileKey} -> $regionId")
				}
				// 存在確認日時を設定
				Tables.Portals.filter{ _.id === p.id}.map{ _.verifiedAt }.update(tm)
				false
			case None =>
				// 有効範囲のものだけ新規ポータルとして追加
				if(context.geocode.available(cp.latE6/1e6, cp.lngE6/1e6)){
					Tables.Portals.map{ x =>
						(x.guid, x.title, x.image, x.tileKey, x.late6, x.lnge6, x.createdAt, x.updatedAt)
					}.insert((cp.guid, cp.title, cp.image, regionId, cp.latE6, cp.lngE6, tm, tm))
					savePortalEvent(Tables.Portals.filter{ _.guid === cp.guid }.map{ _.id }.first, "create", tm)
					setGeoHashAsync(cp.guid, cp.latE6, cp.lngE6).onComplete{
						case Success(Some(l)) =>
							logger.info(s"新規ポータルが登録されました: ${cp.guid}; ${cp.latE6}/${cp.lngE6}; ${cp.title}; ${l.city}, ${l.state}, ${l.country}")
						case Success(None) =>
							logger.info(s"新規ポータルが登録されました: ${cp.guid}; ${cp.latE6}/${cp.lngE6}; ${cp.title}; (住所不明)")
						case Failure(ex) =>
							logger.info(s"新規ポータルが登録されました: ${cp.guid}; ${cp.latE6}/${cp.lngE6}; ${cp.title}")
							logger.error(s"住所の取得に失敗しました", ex)
					}
					true
				} else false
		}
	}

	/**
	 * ポータルの状況変更をイベントログへ保存。
	 */
	private[this] def savePortalEvent(portalId:Int, action:String, oldValue:String, newValue:String, verifiedAt:Timestamp, tm:Timestamp)(implicit session:Session):Unit = savePortalEvent(portalId, action, Some(oldValue), Some(newValue), Some(verifiedAt), tm)
	private[this] def savePortalEvent(portalId:Int, action:String, tm:Timestamp)(implicit session:Session):Unit = savePortalEvent(portalId, action, None, None, None, tm)
	private[this] def savePortalEvent(portalId:Int, action:String, verifiedAt:Timestamp, tm:Timestamp)(implicit session:Session):Unit = savePortalEvent(portalId, action, None, None, Some(verifiedAt), tm)
	private[this] def savePortalEvent(portalId:Int, action:String, oldValue:Option[String], newValue:Option[String], verifiedAt:Option[Timestamp], tm:Timestamp)(implicit session:Session):Unit = Tables.PortalEventLogs.map{ x =>
		(x.portalId, x.action, x.oldValue, x.newValue, x.verifiedAt, x.createdAt)
	}.insert((portalId, action, oldValue, newValue, verifiedAt, tm))

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
	private[this] def setGeoHashAsync(guid:String, latE6:Int, lngE6:Int):Future[Option[GeoCode.Location]] = {
		// 行政区情報は API を使用するため非同期で設定
		context.geocode.getLocation(latE6/1e6, lngE6/1e6).andThen {
			case Success(Some(location)) =>
				context.database.withSession { implicit session =>
					Tables.Portals
						.filter{ _.guid === guid }.map{ x => (x.geohash, x.updatedAt) }
						.update((Some(location.geoHash), new Timestamp(System.currentTimeMillis())))
				}
		}
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
