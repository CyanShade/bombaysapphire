package controllers

import java.sql.Timestamp
import java.text.SimpleDateFormat

import models.Tables
import org.slf4j.LoggerFactory
import play.api.db.slick.DBAction
import play.api.libs.json._
import play.api.mvc._

import scala.slick.driver.PostgresDriver.simple._
import scala.util.Try

object Portals extends Controller {
  import controllers.Application._
  private[this] val logger = LoggerFactory.getLogger(getClass)

  def index = Action {
    Ok(views.html.portals())
  }

  // ==============================================================================================
  // イベントログの表示
  // ==============================================================================================
  def eventlogs = Action {
    Ok(views.html.eventlogs())
  }

  // ==============================================================================================
  // ポータル詳細情報の取得
  // ==============================================================================================
  def portal(id:Int) = DBAction{ implicit  rs =>
    implicit val session = rs.dbSession
    val df = new SimpleDateFormat("yyyy/MM/dd HH:mm")
    Tables.Portals.filter{ _.id === id }.firstOption match {
      case Some(portal) =>
        val states = Tables.PortalStateLogs.filter{ _.portalId === id }.sortBy{ _.createdAt.desc }.list
        val events = Tables.PortalEventLogs.filter{ _.portalId === id }.sortBy{ _.createdAt.desc }.list
        Ok(Json.toJson(
          Json.obj(
            "id" -> portal.id,
            "guid" -> portal.guid,
            "tile_key" -> portal.tileKey,
            "latlng" -> Json.arr(portal.late6/1e6, portal.lnge6/1e6),
            "title" -> portal.title,
            "image" -> portal.image,
            "team" -> portal.team,
            "level" -> portal.level,
            "guardian" -> portal.guardian,
            "created_at" -> df.format(portal.createdAt),
            "verified_at" -> df.format(portal.verifiedAt),
            "deleted_at" -> portal.deletedAt.map{ t => df.format(t) }.getOrElse(null),
            "state_log" -> states.map{ state =>
              Json.obj(
                "owner" -> state.owner.getOrElse(null),
                "level" -> state.level,
                "health" -> state.health,
                "team" -> state.team,
                "mitigation" -> state.mitigation.map{_.toInt}.getOrElse(-1).toInt,
                "res_count" -> state.resCount,
                "resonators" -> Json.arr(),
                "mods" -> Json.arr(),
                "created_at" -> df.format(state.createdAt)
              )
            },
            "event_log" -> events.map{ event =>
              Json.obj(
                "action" -> event.action,
                "old_value" -> event.oldValue,
                "new_value" -> event.newValue,
                "created_at" -> df.format(event.createdAt)
              )
            }
          )
        ))
      case None => NotFound
    }
  }

  // ==============================================================================================
  // ポータル一覧の取得
  // ==============================================================================================
  def portals(fmt:String) = DBAction { implicit rs =>
    implicit val session = rs.dbSession
    val df = new SimpleDateFormat("yyyy/MM/dd HH:mm")
    val query = rs.request.queryString.map{ case (key, values) => key -> values(0) }

    val additionalHeaders = (if(query.get("dl").contains("on"))
      Seq("Content-Disposition" -> s"attachment; filename=portals.$fmt")
    else
      Seq()
    ) ++ Seq(
      "Cache-Control" -> "no-cache"
    )

    val limit = query.get("limit").map {_.toInt}.getOrElse(4000)
    val portals = search(query.-("dl", "limit"), limit)

    fmt.toLowerCase match {
      case "json" =>
        Ok(Json.toJson(portals.map { case p =>
          Json.obj(
            "id" -> p.id,
            "title" -> p.title,
            "image" -> p.image,
            "latlng" -> Json.arr( p.latE6/1e6, p.lngE6/1e6 ),
            "team" -> p.team,
            "level" -> p.level,
            "guardian" -> p.guardian,
            "country" -> p.country.getOrElse(null),
            "state" -> p.state.getOrElse(null),
            "city" -> p.city.getOrElse(null),
            "created_at" -> df.format(p.createdAt)
          )
        }.toSeq)).withHeaders(additionalHeaders:_*)
      case "cvs" =>
        def c(s:String):String = "\"" + s.replace("\"", "\"\"") + "\""
        def i(i:AnyVal):String = i.toString
        val csv = portals.map { case p =>
          s"${i(p.id)},${c(p.title)},${c(p.image)},${c(p.country.getOrElse(""))},${c(p.state.getOrElse(""))},${c(p.city.getOrElse(""))},${i(p.latE6/1e6)},${i(p.lngE6/1e6)},${c(p.team)},${i(p.level)},${i(p.guardian)},${c(df.format(p.createdAt))}"
        }.mkString("\n")
        Ok(csv).withHeaders(additionalHeaders:_*).as("text/csv")
      case "kml" =>
        // <?xml version="1.0" encoding="UTF-8"?>
        // <kml xmlns="http://www.opengis.net/kml/2.2">
        Ok(
          <kml>
            <Folder>
            { portals.map { p =>
            <Placemark>
              <name>{p.title}</name>
              <description>&lt;h4&gt;{p.title}&lt;/h4&gt;&lt;img src="{p.image}"/&gt;</description>
              <Point>
                <coordinates>{p.lngE6/1e6},{p.latE6/1e6},0</coordinates>
              </Point>
            </Placemark> }}
            </Folder>
          </kml>
        ).withHeaders(additionalHeaders:_*)
      case _ => BadRequest
    }
  }

  /**
   * ポータルのイベントを JSON 形式で返す。
   * p:ページ番号
   * i:1ページあたりの項目数
   */
  def events = DBAction { implicit rs =>
    implicit val session = rs.dbSession
    val df = new SimpleDateFormat("yyyy/MM/dd HH:mm")
    val page = rs.request.queryString.get("p").flatMap{ p => Try(p(0).toInt).toOption }.getOrElse(0)
    val items = rs.request.queryString.get("i").flatMap{ i => Try(i(0).toInt).toOption }.getOrElse(15)
    val count = Tables.PortalEventLogs.length.run
    val json = Json.obj(
      "page" -> page,
      "items_per_page" -> items,
      "max_page" -> math.max(0, (count - 1) / items),
      "items_count" -> count,
      "items" -> Tables.PortalEventLogs
        .leftJoin(Tables.Portals).on{ _.portalId === _.id }
        .leftJoin(Tables.Geohash).on{ _._2.geohash === _.geohash}
        .sortBy{ case ((event,_),_) => event.createdAt.desc }
        .map{ case ((event, portal), geohash) =>
          (event.id, portal.id.?, portal.title.?, portal.image.?, portal.late6.?, portal.lnge6.?, geohash.country.?, geohash.state.?, geohash.city.?, event.action, event.oldValue, event.newValue, event.verifiedAt, event.createdAt)
        }
        .drop(page * items).take(items).list
        .map{ case (id, portalId, title, image, late6, lnge6, country, state, city, action, oldValue, newValue, verifiedAt, createdAt) =>
          Json.obj(
            "id" -> id,
            "portal_id" -> portalId.map{_.toString}.getOrElse(null),
            "title" -> title.getOrElse(null),
            "image" -> image.getOrElse(null),
            "latlng" -> Json.arr( late6.map{_/1e6}.map{_.toString}.getOrElse(null), lnge6.map{_/1e6}.map{_.toString}.getOrElse(null) ),
            "country" -> country.getOrElse(null),
            "state" -> state.getOrElse(null),
            "city" -> city.getOrElse(null),
            "action" -> action,
            "old_value" -> oldValue,
            "new_value" -> newValue,
            "message" -> (action match {
              case "create" => s"新規ポータル '$title' を検出しました"
              case "remove" => s"ポータル '$title' が削除されました"
              case "change_title" => s"ポータル '$oldValue' の名称が '$newValue' に変更されました"
              case unknown => unknown
            }),
            "verified_at" -> verifiedAt.map{ df.format }.getOrElse(null),
            "created_at" -> df.format(createdAt)
          )
        }.toSeq
      )
    Ok(Json.toJson(json))
  }


  def search(query:Map[String,String], limit:Int)(implicit session:play.api.db.slick.Session):Seq[Portal] = {
    val param = query.filterNot{ _._2.trim().isEmpty }
    logger.debug(s"portal locations: ${param.filterNot{ _._2.trim().isEmpty }.toSeq.map{ case (k,v)=>s"$k=$v"}.mkString(",")}")
    param
      .foldLeft(Tables.Portals.leftJoin(Tables.Geohash).on(_.geohash === _.geohash)) { case (table, (key, value)) =>
      key match {
        case "title" =>
          value.split("[\\s　]+")
            .map{ _.replace("\\", "\\\\").replaceAll("([%_])", "\\$1") }
            .foldLeft(table){ case (t,kwd) => t.filter{ case (p, g) => p.title.like("%" + kwd + "%") } }
        case "created_at>" =>
          toTimestamp(value) match {
            case Some(tm) => table.filter { case (p, g) => p.createdAt >= tm }
            case None => throw new BadRequest(s"invalid date format: $value")
          }
        case "created_at<" =>
          toTimestamp(value) match {
            case Some(tm) => table.filter { case (p, g) => p.createdAt < tm }
            case None => throw new BadRequest(s"invalid date format: $value")
          }
        case "late6>" => table.filter { case (p, g) => p.late6 >= value.toInt}
        case "late6<" => table.filter { case (p, g) => p.late6 < value.toInt}
        case "lnge6>" => table.filter { case (p, g) => p.lnge6 >= value.toInt}
        case "lnge6<" => table.filter { case (p, g) => p.lnge6 < value.toInt}
        case "tile_key" => table.filter { case (p, g) => p.tileKey === value}
        case "country" => table.filter { case (p, g) => g.country === value}
        case "state" => table.filter { case (p, g) => g.state === value}
        case "city" => table.filter { case (p, g) => g.city === value}
        case "cll" =>
          val Array(clat, clng) = value.split(",", 2).map{ l => Try(l.toDouble).toOption.getOrElse(Double.NaN) }
          if(! clat.isNaN && ! clng.isNaN){
            table.sortBy { case (p, g) =>
              (p.late6.asColumnOf[Double]/1e6 - clat) * (p.late6.asColumnOf[Double]/1e6 - clat) +
                (p.lnge6.asColumnOf[Double]/1e6 - clng) * (p.lnge6.asColumnOf[Double]/1e6 - clng) }
          } else table
        case "bounds" =>
          if(value == ""){
            table
          } else
          value.split(",").map{ s => (s.toDouble * 1e6).toInt } match {
            case Array(lat1, lng1, lat0, lng0) =>
              table.filter{ case (p, g) => p.late6 >= lat0 && p.late6 <= lat1 && p.lnge6 >= lng0 && p.lnge6 <= lng1 }
            case _ => throw new BadRequest(s"unsupported bounds format: $value")
          }
        case unknown => throw new BadRequest(s"unknown parameter: $unknown=$value")
      }
    }
      //.sortBy { case (p, g) => (g.country, g.state, g.city) }
      .take(limit)
      .map { case (p, g) => (p.id, p.title, p.image, p.late6, p.lnge6, p.team, p.level, p.guardian, p.createdAt, p.verifiedAt, g.country.?, g.state.?, g.city.?)}
      .list.map{ case (id, title, image, latE6, lngE6, team, level, guardian, createdAt, verifiedAt, country, state, city) =>
        val diff = System.currentTimeMillis() - verifiedAt.getTime
        Portal(id, title, image, latE6, lngE6, team, level.toInt, guardian + diff, createdAt, country, state, city)
      }
  }

  case class Portal(id:Int, title:String, image:String, latE6:Int, lngE6:Int, team:String, level:Int, guardian:Long, createdAt:Timestamp, country:Option[String], state:Option[String], city:Option[String])

}