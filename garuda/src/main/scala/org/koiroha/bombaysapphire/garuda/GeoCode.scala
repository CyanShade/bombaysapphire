/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire.garuda

import java.io.IOException
import java.net.URL
import java.util.concurrent.{Executors, ThreadFactory}

import ch.hsr.geohash.GeoHash
import org.json4s.native.JsonMethods._
import org.json4s.{DefaultFormats, JArray, JValue}
import org.koiroha.bombaysapphire.schema.Tables
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.slick.driver.PostgresDriver.simple._
import scala.slick.jdbc.StaticQuery.interpolation

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// GeoCode
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * 緯度/経度から住所を求める処理。Google の geocode API を参照し、結果を 8 桁の GeoHash としてキャッシュする。
 * GeoHash の桁数と精度の関係については http://d.hatena.ne.jp/hfu/20080603/1212439145 を参照。1秒≒31m で
 * あるため Base32 10 桁で 1.2m×0.6m の範囲に収まる。
 *
 * @author Takami Torao
 */
class GeoCode private[garuda](context:Context) {
	import org.koiroha.bombaysapphire.garuda.GeoCode._
	private[this] implicit val _Format = DefaultFormats

	/**
	 * Geocode API に負荷をかけないために最大でも 1 スレッドで問い合わせを行う。
	 */
	private[this] implicit val worker = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor(new ThreadFactory {
		override def newThread(r:Runnable):Thread = {
			val thread = new Thread(r, "GeoCode Resolver")
			thread.setDaemon(true)
			thread.setPriority(Thread.MIN_PRIORITY)
			thread
		}
	}))

	/**
	 * 指定された地点が heuristic_regions テーブルに既知の行政区域として登録されている場合にその情報を返す。
	 */
	def heuristicLocation(lat:Double, lng:Double)(implicit s:Session):Option[(String,String,String)] = {
		sql"select country,state,city from intel.heuristic_regions where point '(#$lat,#$lng)' @ region and side='I'".as[(String,String,String)].firstOption
	}

	/**
	 * 全世界に対して API を使用しないための geocode 有効範囲を判定。
	 * 現在日本の領域内でのみ有効。
	 */
	def available(lat:Double, lng:Double):Boolean = true

	/**
	 * 指定された緯度/経度の位置情報を取得。
	 * 有効範囲外の場合は None が通知される
	 */
	def getLocation(lat:Double, lng:Double):Future[Option[Location]] = {
		if(available(lat, lng)){
			val geoHash = GeoHash.withCharacterPrecision(lat, lng, 10).toBase32
			Future { Some(load(geoHash)) }
		} else {
			Future.successful(None)
		}
	}

	/**
	 * 指定された GeoHash の位置情報を取得し DB に保存する。
	 */
	private[this] def load(gh:String):Location = context.database.withSession { implicit s =>

		Tables.Geohash.filter {_.geohash === gh}.firstOption match {
			case Some(g) =>
				// 既に取得済みであればそれを返す
				Location(gh, g.country, g.state, g.city)
			case None =>
				// geohash の該当する位置の情報を取得
				// (geohash を基準に管理しているためポータルそのものの位置は使用しない)
				val p = GeoHash.fromGeohashString(gh).getPoint
				val lat = p.getLatitude
				val lng = p.getLongitude
				val (country, state, city) = heuristicLocation(lat, lng) match {
					case Some(csc) =>
						// 既知の領域にヒットした場合はそのまま返す
						logger.debug("hit heuristics region")
						csc
					case None =>
						// OVER_QUERY_LIMIT が発生している場合は 10 分間は再問い合わせを行わない。
						if(overlimit.exists(_ + 60 * 60 * 1000 > System.currentTimeMillis())){
							throw new IOException("OVER_QUERY_LIMIT")
						}
						logger.debug("calling remote geocode api")
						val con = new URL(s"http://maps.googleapis.com/maps/api/geocode/json?latlng=$lat,$lng&sensor=false").openConnection()
						con.setRequestProperty("Accept-Language", s"${context.locale.language},en;q=0.8")
						val result = parse(con.getInputStream)
						(result \ "status").extract[String] match {
							case "OK" =>
								// 短時間に問い合わせをすると OVER_LIMIT に達するため 2 秒待てという仕様
								Thread.sleep(500)
								// 結果は複数返ることがあるが先頭のものが最も詳細だがバス停などが入っていることがある
								findPoliticalAddress(result \ "results") match {
									case (Some(c), Some(st), Some(ct)) => (c, st, ct)
									case (Some(c), Some(st), None) => (c, st, "(該当なし)")
									case (Some(c), None, _) => (c, "(該当なし)", "(該当なし)")
									case (None, _, _) => ("__", "(該当なし)", "(該当なし)")
								}
							case "OVER_QUERY_LIMIT" =>
								overlimit = Some(System.currentTimeMillis())
								throw new IOException("OVER_QUERY_LIMIT")
							case unknown =>
								throw new IOException(s"goecode api error: $unknown")
						}
				}
				// 取得した位置情報の保存
				Tables.Geohash
					.map{ g => (g.geohash, g.late6, g.lnge6, g.country, g.state, g.city) }
					.insert((gh, (lat * 1e6).toInt, (lng * 1e6).toInt, country, state, city))
				logger.debug(f"retrieve location: $lat%.6f/$lng%.6f; $country $state $city")
				Location(gh, country, state, city)
		}
	}

	private[this] def findPoliticalAddress(value:JValue):(Option[String],Option[String],Option[String]) = value match {
		case as:JArray =>
			/*
			 * address_components: [
			 *   {
			 *     short_name: "JP",
			 *     types: [ "country", "political" ]
			 * ]
			*/
			val ac = as.arr
				.map{ _ \ "address_components" }.collect{ case a:JArray => a }
			val country = ac.flatMap{ a =>
				a.find{ a => hasTypes(a \ "types", "country", "political") }
					.map{ a => (a \ "short_name").extract[String]}
			}.headOption
			val state = ac.flatMap{ a =>
				a.find{ a => hasTypes(a \ "types", "administrative_area_level_1", "political") }
					.map{ a => (a \ "short_name").extract[String]}
			}.headOption
			// colloquial_area は駅を示す情報に対して "東京" などが入ってしまうため先頭でのみ評価
			val city1 = ac(0).find{ a => hasTypes(a \ "types", "colloquial_area", "locality", "political") }
					.map{ a => (a \ "short_name").extract[String]}
			val city2 = ac.flatMap{ a =>
				a.find{ a => hasTypes(a \ "types", "locality", "political") }
					.map{ a => (a \ "short_name").extract[String]}
			}.headOption
			val city = (city1, city2) match {
				case (Some(c1), Some(c2)) => Some(s"$c1$c2")
				case (c1, c2) => c1.orElse(c2)
			}
			(country, state, city)
		case _ =>
			(None, None, None)
	}

	private[this] def hasTypes(list:JValue, values:String*):Boolean = list match {
		case l:JArray =>
			val x = l.arr.flatMap{ _.extractOpt[String] }
			x.length == values.length && x.intersect(values).length == values.length
		case _ => false
	}

	/**
	 * OVER_QUERY_LIMIT が発生した時刻。
	 */
	private[this] var overlimit:Option[Long] = None

}

object GeoCode {
	private[GeoCode] val logger = LoggerFactory.getLogger(getClass)

	/**
	 * GeoHash に基づく位置情報。
	 */
	case class Location(geoHash:String, country:String, state:String, city:String)
}