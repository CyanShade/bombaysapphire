package controllers

import java.sql.Timestamp

import akka.actor.Cancellable
import models.Tables
import org.koiroha.bombaysapphire.BombaySapphire
import org.slf4j.LoggerFactory
import play.api.db.slick.DBAction
import play.api.libs.concurrent.Akka
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.Json
import play.api.mvc._
import play.api.Play.current

import scala.slick.driver.PostgresDriver.simple._
import scala.concurrent.duration._

object Sentinel extends Controller {
  private[this] val logger = LoggerFactory.getLogger(getClass)

  def tasks = DBAction { rs =>
    implicit val _session = rs.dbSession
    val now = new Timestamp(System.currentTimeMillis())
    _session.withTransaction{
      Ok(Json.arr(Tables.SentinelTasks
        .filter{ s => s.expiredAt.isEmpty || s.expiredAt < now}
        .sortBy{ s => (s.priority, s.createdAt, s.tag) }
        .take(10).list.map{ task =>
          Tables.SentinelTasks.filter{ _.id === task.id }.delete
          Json.obj("eval" -> task.script, "wait" -> task.waitAfter)
        }
      ))
    }
  }

  private[this] var cancels = Seq[Cancellable]()

  def startBatches():Unit = {
    stopBatches()
    cancels = cancels :+ Akka.system.scheduler.schedule(0.seconds, 15.minutes){
      monitorPortals()
    }
  }

  def stopBatches():Unit = {
    cancels.foreach{ _.cancel() }
  }

  /**
   * ファームに属するポータルの詳細情報表示タスクを追加。
   */
  private[this] def monitorPortals():Unit = play.api.db.slick.DB.withTransaction{ implicit _session =>
    val now = new Timestamp(System.currentTimeMillis())
    val ll = for {
      (f, p) <- Tables.FarmPortals innerJoin Tables.Portals on (_.portalId === _.id)
    } yield (f.farmId, p.id, p.late6, p.lnge6)
    ll.run.distinct.foreach{ case (fid, pid, lat, lng) =>
      val tag = s"F${fid}P$pid"
      if(! Tables.SentinelTasks.filter{ _.tag === tag }.exists.run) {
        val script = s"document.location='https://${BombaySapphire.RemoteHost}/intel?pll=${lat/1e6},${lng/1e6}';"
        Tables.SentinelTasks
          .map{ s => (s.tag, s.priority, s.script, s.waitAfter, s.createdAt) }
          .insert((tag, 5.toShort, script, 10 * 1000L, now))
      }
    }
  }

}