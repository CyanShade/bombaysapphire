package controllers

import play.api._
import play.api.mvc._
import play.api.db.slick.DBAction
import org.slf4j.LoggerFactory

object Application extends Controller {
  private[this] val logger = LoggerFactory.getLogger(getClass)

  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }

  def locations = DBAction { implicit session =>
    logger.debug(s"portal locations: ${param.filterNot{ _._2.trim().isEmpty }.toSeq.map{ case (k,v)=>s"$k=$v"}.mkString(",")}")
    val json = compact(render(param.filterNot{ _._2.trim().isEmpty }
      .foldLeft(Portals.leftJoin(Geohash).on(_.geohash === _.geohash)) { case (table, (key, value)) =>
      key match {
        case "created_at>" =>
          toTimestamp(value) match {
            case Some(tm) => table.filter { case (p, g) => p.createdAt >= tm }
            case None => throw BadRequest(s"invalid date format: $value")
          }
        case "created_at<" =>
          toTimestamp(value) match {
            case Some(tm) => table.filter { case (p, g) => p.createdAt < tm }
            case None => throw BadRequest(s"invalid date format: $value")
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
        case unknown => throw BadRequest(s"unknown parameter: $unknown=$value")
      }
    }
      //.sortBy { case (p, g) => (g.country, g.state, g.city) }
      .map { case (p, g) => (p.id, p.title, p.image, p.late6, p.lnge6, p.createdAt, g.country.?, g.state.?, g.city.?)}
      .run.map { case (id, title, image, lat, lng, createdAt, country, state, city) =>
      ("id" -> id) ~
        ("title" -> title) ~
        ("image" -> image) ~
        ("latlng" -> List(lat / 1e6, lng / 1e6)) ~
        ("country" -> country.getOrElse("")) ~
        ("state" -> state.getOrElse("")) ~
        ("city" -> city.getOrElse("")) ~
        ("created_at" -> toString(createdAt))
    }.toList))
    respond(200, json.getBytes("UTF-8"), "application/json")

    Ok(views.html.index("Your new application is ready."))
  }
}