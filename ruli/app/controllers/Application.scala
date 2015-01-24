package controllers

import java.sql.Timestamp
import java.text.{DateFormat, SimpleDateFormat}

import models.Tables
import org.slf4j.LoggerFactory
import play.api.db.slick.DBAction
import play.api.libs.json._
import play.api.mvc._

import scala.slick.driver.PostgresDriver.simple._
import scala.util.Try

object Application extends Controller {
  private[this] val logger = LoggerFactory.getLogger(getClass)

  def index = Action {
    Ok(views.html.portals())
  }

  def eventlogs = Action {
    Ok(views.html.eventlogs())
  }

  def locations = DBAction { implicit rs =>
    implicit val session = rs.dbSession
    val df = new SimpleDateFormat("yyyy/MM/dd HH:mm")
    val param = rs.request.queryString.map{ case (key, values) => key -> values(0) }.filterNot{ _._2.trim().isEmpty }
    logger.debug(s"portal locations: ${param.filterNot{ _._2.trim().isEmpty }.toSeq.map{ case (k,v)=>s"$k=$v"}.mkString(",")}")
    val portals = param
      .foldLeft(Tables.Portals.leftJoin(Tables.Geohash).on(_.geohash === _.geohash)) { case (table, (key, value)) =>
        key match {
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
          case unknown => throw new BadRequest(s"unknown parameter: $unknown=$value")
        }
      }
      //.sortBy { case (p, g) => (g.country, g.state, g.city) }
      .map { case (p, g) => (p.id, p.title, p.image, p.late6, p.lnge6, p.createdAt, g.country.?, g.state.?, g.city.?)}
      .run.map { case (id, title, image, lat, lng, createdAt, country, state, city) =>
        Json.obj(
          "id" -> id,
          "title" -> title,
          "image" -> image,
          "latlng" -> Json.arr( lat/1e6, lng/1e6 ),
          "country" -> country.get,
          "state" -> state.get,
          "city" -> city.get,
          "created_at" -> df.format(createdAt)
        )
      }.toSeq
    Ok(Json.toJson(portals))
  }

  def regions = Action {
    Ok(views.html.index("Your new application is ready."))
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
        .drop(page * items).take(items).list
        .map{ case ((event, portal), geohash) =>
          Json.obj(
            "id" -> event.id,
            "portal_id" -> portal.id,
            "title" -> portal.title,
            "image" -> portal.image,
            "latlng" -> Json.arr( portal.late6/1e6, portal.lnge6/1e6 ),
            "country" -> geohash.country,
            "state" -> geohash.state,
            "city" -> geohash.city,
            "action" -> event.action,
            "old_value" -> event.oldValue,
            "new_value" -> event.newValue,
            "message" -> (event.action match {
              case "create" => s"新規ポータル '${portal.title}' を検出しました"
              case "remove" => s"ポータル '${portal.title}' が削除されました"
              case "change_title" => s"ポータル '${event.oldValue}' の名称が '${event.newValue}' に変更されました"
              case unknown => unknown
            }),
            "verified_at" -> event.verifiedAt.map{ df.format },
            "created_at" -> df.format(event.createdAt)
          )
        }.toSeq
      )
    Ok(Json.toJson(json))
  }

  class BadRequest(msg:String) extends Exception(msg)

  private[this] def toTimestamp(value:String):Option[Timestamp] = (if(value.matches("\\d+")) {
    // 2014-12-30 の様な表記が -1 月と解釈されるようなので別評価とする
    Seq("yyyyMMddHHmmss", "yyyyMMddHHmm", "yyyyMMddHH", "yyyyMMdd")
  } else {
    Seq(
      "yyyy/MM/dd H:mm:ss", "yyyy-MM-dd H:mm:ss",
      "yyyy/MM/dd H:mm", "yyyy-MM-dd H:mm",
      "yyyy/MM/dd H", "yyyy-MM-dd H",
      "yyyy/MM/dd", "yyyy-MM-dd")
  }).flatMap{ fmt =>
    val df = new SimpleDateFormat(fmt)
//    df.setTimeZone(Context.Locale.timezone)
    Try{ new Timestamp(df.parse(value).getTime) }.toOption
  }.headOption.map{ x =>
    logger.debug(s"timestamp: $value -> ${DateFormat.getDateTimeInstance.format(x)}")
    x
  }
  private[this] def toString(value:Timestamp):String = {
    val df = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT)
//    df.setTimeZone(Context.Locale.timezone)
    df.format(value)
  }
}
