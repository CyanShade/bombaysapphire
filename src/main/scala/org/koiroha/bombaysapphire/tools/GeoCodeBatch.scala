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
		deleteIneffectualPortals()
		updateUnlocatedPortals()
		true
	} finally { batchRunning.set(false) } else false

	/**
	 * 有効範囲外のポータルを削除。
	 */
	def deleteIneffectualPortals():Unit = {
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
			logger.info(s"領域外ポータルが $deleted 個削除されました")
		}
	}

	/**
	 * GeoHash に登録されていないポータルに対して Google Map API から逆ジオコードで住所を取得し設定。
	 */
	def updateUnlocatedPortals():Unit = {
		val (updated, skipped, _) = Context.Database.withSession { implicit session =>
			Tables.Portals
				.filter {_.geohash.isEmpty}
				.map { p => (p.id, p.late6, p.lnge6) }.run
		}.foldLeft((0, 0, false)) { case ((count, skip, error), (id, latE6, lngE6)) =>
			if(error){
				(count, skip, true)
			} else {
				val future = GeoCode.getLocation(latE6 / 1e6, lngE6 / 1e6)
				Try{ Await.result(future, Duration.Inf) } match {
					case Success(Some(location)) =>
						Context.Database.withSession { implicit s =>
							Tables.Portals.filter {_.id === id}.map { p => (p.geohash, p.updatedAt)}
								.update(Some(location.geoHash), new Timestamp(System.currentTimeMillis()))
							logger.info(s"update portal location: [$id] ${location.country} ${location.state} ${location.city}")
						}
						(count + 1, skip, false)
					case Success(None) => (count, skip, false)
					case Failure(ex) =>
						logger.error(ex.toString)
						(count, skip + 1, true)
				}
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