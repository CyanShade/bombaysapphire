/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire.tools

import java.sql.Timestamp
import java.util.concurrent.atomic.AtomicBoolean

import org.koiroha.bombaysapphire.schema.Tables
import org.koiroha.bombaysapphire.{Batch, Context, GeoCode}
import org.slf4j.LoggerFactory

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.slick.driver.PostgresDriver.simple._
import scala.util.{Failure, Success, Try}

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// GeoCodeBatch
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * geocode の設定されていないポータルに位置情報を設定する。
 * Google のレートリミットが1日 2,000 程度。
 * @author Takami Torao
 */
object GeoCodeBatch {
	private[this] val logger = LoggerFactory.getLogger(getClass)

	private[this] val batchRunning = new AtomicBoolean(false)

	/**
	 * バッチ処理の実行を要求。既に実行中であれば false を返す。
	 */
	def requestBatch():Boolean = if(batchRunning.compareAndSet(false, true)) try {
		// deleteIneffectualPortals()
		updateUnlocatedPortals()
		// check()
		true
	} finally { batchRunning.set(false) } else false

	def check() = Context.Database.withSession { implicit session =>
		logger.info("取得済みポータルのうち調査対象領域に含まれている件数を計算しています...")
		val (inside, outside) = Tables.Portals.map{ p => (p.late6, p.lnge6) }.run
			.foldLeft((0,0)){ case ((x,y), (latE6, lngE6)) =>
				if(Context.Region.contains(latE6/1e6, lngE6/1e6)){ (x+1, y) } else { (x, y+1) }
		}
		logger.info(f"調査領域内: $inside%,d 地点")
		logger.info(f"調査領域外: $outside%,d 地点 (API問い合わせが発生するもの)")

		logger.info("既に解決済みの位置情報を現在の heuristic_regions の内容で検証しています...")
		val (success, failure, ignore) = Tables.Geohash.map{ g => (g.late6, g.lnge6, g.country, g.state, g.city) }.run
			.foldLeft((0,0,0)){ case ((x,y,z), (latE6, lngE6, country, state, city)) =>
			GeoCode.heuristicLocation(latE6/1e6, lngE6/1e6) match {
				case Some((c, st, ct)) =>
					if(c == country && state == st && city == ct){
						(x+1,y,z)
					} else {
						(x,y+1,z)
					}
				case None => (x, y, z+1)
			}
		}
		logger.info(f"矛盾なし: $success%,d 地点")
		logger.info(f"矛盾あり: $failure%,d 地点")
		logger.info(f"該当なし: $ignore%,d 地点 (API問い合わせが発生するもの)")

		logger.info("未解決の位置情報について heuristic_regions で解決可能な件数を計算しています...")
		val (avail, unavail) = Tables.Portals.filter{ _.geohash.isEmpty }.map{ p => (p.late6, p.lnge6) }.run
			.foldLeft((0,0)){ case ((x,y), (latE6, lngE6)) =>
			GeoCode.heuristicLocation(latE6/1e6, lngE6/1e6) match {
				case Some((c, st, ct)) => (x+1, y)
				case None => (x, y+1)
			}
		}
		logger.info(f"解決可能: $avail%,d 地点")
		logger.info(f"該当なし: $unavail%,d 地点 (API問い合わせが発生するもの)")
	}

	/**
	 * 有効範囲外のポータルを削除。
	 */
	def deleteIneffectualPortals():Unit = {
		logger.info("調査領域外のポータルを削除しています...")
		val deleted = Context.Database.withSession { implicit session =>
			Tables.Portals
				.filter {_.geohash.isEmpty}
				.map { p => (p.id, p.late6, p.lnge6)}.run
				.collect { case (id, late6, lnge6) if !GeoCode.available(late6 / 1e6, lnge6 / 1e6) => id}
				.map { id =>
				Tables.Portals.filter {_.id === id}.delete
				logger.info(s"delete out-of-range portal: $id")
			}.size
		}
		if(deleted > 0){
			logger.info(s"調査領域外のポータルが $deleted 地点削除されました")
		}
	}

	/**
	 * GeoHash に登録されていないポータルに対して Google Map API から逆ジオコードで住所を取得し設定。
	 */
	def updateUnlocatedPortals():Unit = {
		val (updated, skipped, _) = Context.Database.withSession { implicit session =>
			Tables.Portals
				.filter {_.geohash.isEmpty}
				.sortBy{ _.createdAt.desc}
				.map { p => (p.id, p.late6, p.lnge6) }.run
		}.foldLeft((0, 0, false)) { case ((count, skip, error), (id, latE6, lngE6)) =>
			val future = GeoCode.getLocation(latE6 / 1e6, lngE6 / 1e6)
			Try{ Await.result(future, Duration.Inf) } match {
				case Success(Some(location)) =>
					Context.Database.withSession { implicit s =>
						Tables.Portals.filter {_.id === id}.map { p => (p.geohash, p.updatedAt)}
							.update(Some(location.geoHash), new Timestamp(System.currentTimeMillis()))
						logger.info(s"update portal location: [$id] ${location.country} ${location.state} ${location.city}")
					}
					(count + 1, skip, error)
				case Success(None) =>
					logger.info(s"outside portal location: [$id]")
					(count, skip, error)
				case Failure(ex) =>
					logger.error(ex.toString)
					(count, skip + 1, true)
			}
		}
		logger.info(s"$updated 個の位置情報を取得し $skipped 個をスキップしました")
	}

	/**
	 * 1時間に一度メンテナンスバッチを起動
	 */
	def main(args:Array[String]):Unit = {
		requestBatch()
		locally {
			Batch.runEveryAfter(60 * 60 * 1000){
				GeoCodeBatch.requestBatch()
			}
		}
		synchronized{ wait() }
	}

}