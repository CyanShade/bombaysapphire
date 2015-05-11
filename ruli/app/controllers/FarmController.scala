package controllers

import java.io.File
import java.text.SimpleDateFormat

import models.Tables
import models.farm.Farm
import org.slf4j.LoggerFactory
import play.api.db.slick.DBAction
import play.api.libs.json._
import play.api.mvc._

import scala.slick.driver.PostgresDriver.simple._
import scala.util.Try

object FarmController extends Controller {
  private[this] val logger = LoggerFactory.getLogger(getClass)

  // ==============================================================================================
  // ファーム情報の表示
  // ==============================================================================================
  def farmListView = DBAction { rs =>
    val request = rs.request
    implicit val _session = rs.dbSession
    val page = request.getQueryString("p").map{ _.toInt }.getOrElse(0)
    val items = request.getQueryString("i").map{ _.toInt }.getOrElse(25)
    val farms = Farm(Tables.Farms.drop(page * items).take(items).list)
    Ok(views.html.farms(farms))
  }

  def farmListAPI = DBAction { rs =>
    val request = rs.request
    implicit val _session = rs.dbSession
    val page = request.getQueryString("p").map{ _.toInt }.getOrElse(0)
    val items = request.getQueryString("i").map{ _.toInt }.getOrElse(25)
    val farms = Tables.Farms.drop(page * items).take(items).list
    Ok(views.html.farm_list(farms))
  }

  // ==============================================================================================
  // ファーム詳細/編集画面
  // ==============================================================================================
  def farmJS = Action { Ok(views.js.farm()) }
  def farmAPI(id:Int) = DBAction { rs =>
    implicit val _session = rs.dbSession
    Tables.Farms.filter{ _.id === id }.firstOption match {
      case Some(farm) => Ok(views.html.farm(farm))
      case None => NotFound
    }
  }
  def farmEditView(id:String) = Action {
    Ok(views.html.farm_edit(Try{ id.toInt }.toOption))
  }

  // ==============================================================================================
  // ファーム情報の更新
  // ==============================================================================================
  def farmEdit(id:String) = DBAction(parse.multipartFormData) { rs =>
    val request = rs.request
    implicit val _session = rs.dbSession
    val parentId = Try{ request.body.dataParts("patrnt").head.toInt }.toOption
    val name = request.body.dataParts("name").head
    val desc = request.body.dataParts("description").head
    val formatted = models.util.markdown.format(desc)
    val area = request.body.file("kml").flatMap { kml =>
      import services.io._
      val ext = new File(kml.filename).getExtension
      val file = File.createTempFile("farm_area_", s".$ext")
      try {
        kml.ref.moveTo(file, replace = true)
        models.geom.Area.fromKML(file)
      } finally {
        file.delete()
      }
    }.headOption
    val i = if(id == "new") None else Some(id.toInt)
    Farm.save(i, parentId, name, area, desc, formatted)
    /*
    request.body.file("picture").map { picture =>
      import java.io.File
      val filename = picture.filename
      val contentType = picture.contentType
      picture.ref.moveTo(new File(s"/tmp/picture/$filename"))
    }.getOrElse {
      Redirect(routes.Application.index).flashing(
        "error" -> "Missing file")
    }
    */
    Ok(s"File uploaded: $id xxx")
  }

  def farmAPI(id:String) = DBAction { implicit rs =>
    implicit val _session = rs.dbSession
    rs.request.method match {
      case "GET" =>
        id match {
          case "new" =>
            // ファーム一覧
            val farms = Tables.Farms.map{ t => (t.id, t.name) }.list
            Ok(Json.arr(
              farms.map{ case (id, name) => Json.obj("id" -> id, "name" -> name) }
            ))
          case num if Try{ num.toInt }.isSuccess =>
            // ファーム詳細情報
            Tables.Farms.filter{ _.id === num.toInt }.firstOption match {
              case Some(farm) =>
                Ok(Json.obj(
                  "id" -> farm.id,
                  "parent" -> farm.parent,
                  "name" -> farm.name,
                  "formatted_description" -> farm.formattedDescription,
                  "created_at" -> farm.createdAt.getTime,
                  "updated_at" -> farm.updatedAt.getTime
                ))
              case None => NotFound
            }
        }
      case "POST" => Ok("hello, world")
      case _ => Ok("hello, world")
    }
  }

  // ==============================================================================================
  // イベントログの表示
  // ==============================================================================================
  def farms = DBAction { implicit rs =>
    implicit val session = rs.dbSession
    val df = new SimpleDateFormat("yyyy/MM/dd HH:mm")
    val farms = Tables.Farms.innerJoin(Tables.FarmLogs).on{ _.latestLog === _.id }
      .sortBy{ case (f,l) => f.name }.list
    Ok(Json.arr(
      farms.map{ case (farm, log) =>
        Json.obj(
          "id" -> farm.id,
          "parent" -> farm.parent,
          "name" -> farm.name,
          "portal_count" -> log.portalCount,
          "portal_count_r" -> log.portalCountR,
          "portal_count_e" -> log.portalCountE,
          "p8_count_r" -> log.p8CountR,
          "p8_count_e" -> log.p8CountE,
          "avr_level" -> log.avrLevel,
          "avr_level_r" -> log.avrLevelR,
          "avr_level_e" -> log.avrLevelE,
          "avr_resonator_r" -> log.avrResonatorR,
          "avr_resonator_e" -> log.avrResonatorE,
          "avr_mod_r" -> log.avrModR,
          "avr_mod_e" -> log.avrModE,
          "avr_shielding_r" -> log.avrShieldingR,
          "avr_shielding_e" -> log.avrShieldingE,
          "hack_avail" -> log.hackAvail,
          "created_at" -> df.format(farm.createdAt),
          "updated_at" -> df.format(farm.updatedAt)
        )
      }
    ))
  }

}