package org.koiroha.bombaysapphire.garuda

import java.sql.{SQLException, Timestamp}

import org.koiroha.bombaysapphire.garuda.Entities.Portal
import org.koiroha.bombaysapphire.garuda.schema.Tables
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.slick.driver.PostgresDriver.simple._
import scala.slick.jdbc.StaticQuery.interpolation
import scala.util.{Failure, Success}

package object entities {
  private[this] val logger = LoggerFactory.getLogger(getClass)

  implicit class _Entities(entities:Entities){

    /**
     * エンティティをデータベースに保存。
     * @param tm 取得日時
     */
    def save(tm:Timestamp)(implicit session:Session, context:Context):Unit = {
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
            livePortal(cp.guid) match {
              case Some(current) =>
                updatePortal(tm, tileKey, current, cp)
                savePortalState(current.id, cp, tm)
                (0, 1)
              case None =>
                val id = saveNewPortal(tm, tileKey, cp, context.geocode)
                savePortalState(id, cp, tm)
                (1, 1)
            }
          case _ => (0, 0)
        }.reduceLeftOption{ (a, b) => (a._1+b._1, a._2+b._2) }.getOrElse((0, 0))
      }.toSeq.sortBy{ _._1 }
      val time = System.currentTimeMillis() - start
      portalReport.foreach{ case (tileKey, (newPortals, portals)) =>
        logger.debug(f"entities saved: $tileKey%s; total $portals%,d portals with $newPortals%,d new, $time%,d[ms]")
      }
    }

    /**
     * 新規のポータルをデータベースに保存。
     */
    private[this] def saveNewPortal(tm:Timestamp, regionId:String, cp:Portal, gc:GeoCode)(implicit session:Session, context:Context):Int = {
      // ポータルを追加
      Tables.Portals.map{ x =>
        (x.guid, x.title, x.image, x.tileKey, x.late6, x.lnge6, x.team, x.level, x.guardian, x.createdAt, x.updatedAt)
      }.insert((cp.guid, cp.title, cp.image, regionId, cp.latE6, cp.lngE6, cp.team.symbol.toString, cp.level.toShort, 0L, tm, tm))
      val id = Tables.Portals.filter{ _.guid === cp.guid }.map{ _.id }.first
      // 作成イベントを記録
      savePortalEvent(id, "create", tm)
      // 非同期で行政区を照会して設定
      setGeoHashAsync(cp.guid, cp.latE6, cp.lngE6).onComplete{
        case Success(Some(l)) =>
          logger.info(s"新規ポータルが登録されました: ${cp.guid}; ${cp.latE6}/${cp.lngE6}; ${cp.title}; ${l.city}, ${l.state}, ${l.country}")
        case Success(None) =>
          logger.info(s"新規ポータルが登録されました: ${cp.guid}; ${cp.latE6}/${cp.lngE6}; ${cp.title}; (住所不明)")
        case Failure(ex) =>
          logger.info(s"新規ポータルが登録されました: ${cp.guid}; ${cp.latE6}/${cp.lngE6}; ${cp.title}")
          logger.error(s"住所の取得に失敗しました", ex)
      }
      id
    }

    /**
     * 既存のポータルを更新。
     */
    private[this] def updatePortal(tm:Timestamp, regionId:String, current:Tables.Portals#TableElementType, cp:Portal)(implicit session:Session, context:Context):Unit = {
      val record = Tables.Portals.filter{ x => x.id === current.id }
      val verifiedAt = current.verifiedAt
      // 画像 URL の変更
      if(current.image != cp.image){
        record.map{ p => (p.image,p.updatedAt) }.update((cp.image,tm))
        savePortalEvent(current.id, "change_image", current.image, cp.image, verifiedAt, tm)
        logger.info(s"画像URLを変更しました: ${cp.guid}; ${cp.latE6}/${cp.lngE6}; ${cp.title}")
      }
      // タイトルの変更
      if(current.title != cp.title){
        record.map{ p => (p.title,p.updatedAt) }.update((cp.title,tm))
        savePortalEvent(current.id, "change_title", current.title, cp.title, verifiedAt, tm)
        logger.info(s"タイトルを変更しました: ${cp.guid}; ${cp.latE6}/${cp.lngE6}; ${cp.title}; ${current.title} -> ${cp.title}")
      }
      // 位置の変更
      if(current.late6 != cp.latE6 || current.lnge6 != cp.lngE6){
        record.map{ x =>
          (x.late6,x.lnge6,x.updatedAt)
        }.update((cp.latE6,cp.lngE6,tm))
        savePortalEvent(current.id, "change_location", s"${current.late6/1e6},${current.lnge6/1e6}", s"${cp.latE6/1e6},${cp.lngE6/1e6}", verifiedAt, tm)
        setGeoHashAsync(cp.guid, cp.latE6, cp.lngE6)
        logger.info(s"位置を変更しました: ${cp.guid}; ${cp.latE6}/${cp.lngE6}; ${cp.title}")
      }
      // タイルキーの変更
      if(current.tileKey != regionId){
        record.map{ x => (x.tileKey,x.updatedAt) }.update((regionId,tm))
        // logger.info(s"タイルキーを変更しました: ${cp.guid}; ${cp.latE6}/${cp.lngE6}; ${cp.title}; ${p.tileKey} -> $regionId")
      }
      // ガーディアン日数を計算
      val enemyCaptured = Tables.PortalStateLogs
        .filter{ _.portalId === current.id }
        .sortBy{ _.createdAt.desc }
        .map{ x => (x.team, x.owner, x.createdAt) }
        .list
        .takeWhile{ _._1 == cp.team.symbol.toString }
        .map{ x => (x._2, x._3) }
      val guardian = if(enemyCaptured.size <= 1) 0 else {
        val owner = enemyCaptured.find{ _._1.isDefined }.map{ _._1.get }
        val owned = enemyCaptured.takeWhile{ p => owner.isEmpty || p._1.isEmpty || owner.get == p._1.get }
        owned.head._2.getTime - owned.last._2.getTime
      }
      // 存在確認日時を設定
      Tables.Portals
        .filter{ _.id === current.id}
        .map{ p => (p.team, p.level, p.guardian, p.verifiedAt) }
        .update((cp.team.symbol.toString, cp.level.toShort, guardian, tm))
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

    private[this] def savePortalState(portalId:Int, p:Portal, tm:Timestamp)(implicit session:Session, context:Context):Unit = {
      // 状態ログを保存
      Tables.PortalStateLogs.map{ x =>
        (x.portalId, x.level, x.health, x.team, x.resCount, x.createdAt)
      }.insert((portalId, p.level.toShort, p.health.toShort, p.team.symbol.toString, p.resCount.toShort, tm))
    }

    private[this] def livePortal(guid:String)(implicit session:Session):Option[Tables.Portals#TableElementType] = {
      Tables.Portals.filter{ p => p.guid === guid && p.deletedAt.isEmpty }.firstOption
    }

    /**
     * 緯度/経度に対して最も近い定義済み GeoHash を参照。
     */
    private[this] def setGeoHashAsync(guid:String, latE6:Int, lngE6:Int)(implicit context:Context):Future[Option[GeoCode.Location]] = {
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

    /**
     * 指定された場所で何らかのイベントが発生した通知。
     */
    private[this] def notice(latE6:Int, lngE6:Int, code:String, message:String, args:String*)(implicit session:Session):Unit = {
      logger.info(f"[${latE6/1e6}%.6f:${lngE6/1e6}%.6f] $message")
    }
  }

  implicit class _Plext(plext:Plext) {
    def save(tm:Timestamp)(implicit session:Session):Unit = {
      // この Plext ログが存在しなければ保存
      if(! Tables.Plexts.filter{ _.guid === plext.guid }.exists.run){
        try {
          sqlu"insert into intel.plexts(guid,unknown,category,markup,plext_type,team,text,created_at) values(${plext.guid},${plext.unknown},${plext.categories},${plext.markup}::jsonb,${plext.plextType},${plext.team.symbol.toString},${plext.text},$tm)".first
        } catch {
          case ex:SQLException =>
            logger.debug(s"concurrency write conflict: $plext", ex)
        }
      }
    }
  }

}
