package controllers

import java.io.File
import java.net.URL
import java.sql.Timestamp

import models.Tables
import org.koiroha.bombaysapphire._
import org.slf4j.LoggerFactory
import play.api.Play.current
import play.api.db.slick.DBAction
import play.api.libs.Files
import play.api.libs.concurrent.Akka
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json._
import play.api.mvc._
import services.io._

import scala.concurrent.duration._
import scala.slick.driver.PostgresDriver
import scala.slick.driver.PostgresDriver.simple._
import scala.slick.jdbc.StaticQuery.interpolation
import scala.util.Try

object Farms extends Controller {
  private[this] val logger = LoggerFactory.getLogger(getClass)

  /**
   * ファームアイコンに使用できるPNG画像ファイルの最大サイズ。
   */
  val MaxIconSize = 64 * 1024

  // ==============================================================================================
  // ファーム一覧の表示
  // ==============================================================================================
  def index = DBAction { rs =>
    implicit val _session = rs.dbSession
    val farms = Tables.Farms
      .map{ c => (c.id, c.name, c.address, c.formattedDescription)}
      .list
      .map{ case (fid, name, addr, desc) => models.farms.Summary(fid,name,addr,desc) }
    Ok(views.html.farms(farms))
  }

  // ==============================================================================================
  // ファーム情報の表示
  // ==============================================================================================
  def farm(id:Int) = DBAction { rs =>
    val request = rs.request
    implicit val _session = rs.dbSession
    Tables.Farms.filter{ _.id === id }.firstOption match {
      case Some(f) =>
        val portals = Tables.FarmPortals.filter{ _.farmId === f.id }.length.run
        Ok(views.html.farm(models.farms.Description(f.id, f.name, f.address, f.externalKmlUrl.getOrElse(""), f.formattedDescription, portals)))
      case None => NotFound
    }
  }

  /** ファームアイコンの参照 */
  def icon(id:Int) = DBAction { rs =>
    implicit val _session = rs.dbSession
    Tables.Farms.filter{ _.id === id }.take(1).map{ _.icon.asColumnOf[Option[Array[Byte]]] }.firstOption match {
      case Some(Some(icon)) =>
        Ok(icon).as("image/png")
      case Some(None) =>
        Redirect(routes.Assets.at("images/default-farm-icon.png"))
      case None => NotFound
    }
  }

  // ==============================================================================================
  // ファーム情報編集画面の表示
  // ==============================================================================================
  /**
   * ファーム編集画面を表示。
   */
  def editView(id:Int) = DBAction { rs =>
    implicit val _session = rs.dbSession
    val farm = if(id < 0){
      models.farms.Edit(-1, None, "", "", "", "")
    } else {
      Tables.Farms.filter{ _.id === id }.firstOption.map{ models.farms.Edit.parse }.get
    }
    val farms = Tables.Farms.sortBy{ _.name }.map{ f => (f.id, f.name, f.address) }.list
    Ok(views.html.farm_edit(farm, farms))
  }

  // ==============================================================================================
  // ファーム情報の更新
  // ==============================================================================================
  /**
   * ファーム情報の更新。`id` が負の値の場合は新規登録、それ以外は既存のファームIDとして更新処理を行う。
   */
  def edit(_id:Int):Action[MultipartFormData[Files.TemporaryFile]] = DBAction.apply(parse.multipartFormData) { rs =>
    val request = rs.request
    implicit val _session = rs.dbSession
    val parentId = Try{ request.body.dataParts("parent").head.toInt }.toOption
    val name = request.body.dataParts("name").head
    val desc = request.body.dataParts("description").head
    val kml = request.body.dataParts("external_kml_url").head match {
      case "" => None
      case url => Some(url)
    }
    val id = if(_id < 0) None else Some(_id)
    val icon = request.getFile("icon").flatMap {
      case f if f.length() == 0 => None
      case f => Some(f.toByteArray)
    }

    // ファーム情報の保存
    val farmId = _session.withTransaction {
      val fid = update(id, parentId, name, desc, kml)
      updateIcon(fid, icon)
      fid
    }

    // 非同期でファーム領域情報を更新
    Akka.system.scheduler.scheduleOnce(0.seconds){
      asyncF(farmId)
    }

    Redirect(routes.Farms.farm(farmId)).flashing( "message" -> s"ファーム $name を更新しました")

    /*
    val temp = request.getFile("kml")
    try {
      val kml = temp.flatMap{ f =>
        if(f.getName.endsWith(".kml")) Some(f.toByteArray) else if(f.getName.endsWith(".kmz")){
          services.io.using(new ZipFile(f)){ zfile =>
            zfile.entries().collectFirst {
              case e if e.getName == "doc.kml" =>
                Some(zfile.getInputStream(e).toByteArray)
            } match {
              case Some(e) => e
              case None => throw new IllegalArgumentException("KMZファイルの内容が不正です")
            }
          }
        } else {
          throw new IllegalArgumentException("KML/KMZファイルを指定してください")
        }
      }
      save(i, parentId, name, icon, kml, desc)
      Redirect(routes.Farms.index()).flashing( "message" -> s"ファーム $name を更新しました")
    } catch {
      case ex:IllegalArgumentException =>
        Redirect(routes.Farms.index()).flashing( "message" -> ex.getMessage)
    }
    */
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
  // アクティビティログの表示
  // ==============================================================================================
  /*
  def farms = DBAction { implicit rs =>
    implicit val session = rs.dbSession
    val df = new SimpleDateFormat("yyyy/MM/dd HH:mm")
    val farms = Tables.Farms.innerJoin(Tables.FarmActivities).on{ _.latestLog === _.id }
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
  */

  /**
   * 新規ファームの作成または既存ファームを更新。
   * アイコンは空の状態になる。
   * @return ファームID
   */
  private[this] def update(id:Option[Int], parentId:Option[Int], name:String, desc:String, kml:Option[String])(implicit _session:PostgresDriver.Backend#Session):Int = {
    val now = new Timestamp(System.currentTimeMillis())
    val formatted = models.util.markdown.format(desc)
    id match {
      case Some(i) =>
        Tables.Farms
          .filter{ _.id === i }
          .map{ c => (c.name, c.description, c.formattedDescription, c.externalKmlUrl, c.createdAt, c.updatedAt) }
          .update((name, desc, formatted, kml, now, now))
        i
      case None =>
        Tables.Farms
          .map{ c => (c.name, c.description, c.formattedDescription, c.externalKmlUrl, c.createdAt, c.updatedAt) }
          .insert((name, desc, formatted, kml, now, now))
        Tables.Farms
          .filter{ f => f.createdAt === now && f.updatedAt === now && f.name === name && f.description === desc }
          .map{ _.id }.first
    }
  }

  /**
   * ファームアイコンの更新。
   */
  private[this] def updateIcon(id:Int, icon:Option[Array[Byte]])(implicit _session:PostgresDriver.Backend#Session):Unit = {
    updateBlob(id, "icon", icon)
  }

  /**
   * bytea (Blob) 型カラムの更新。
   * Slick 2.x では bytea 型カラムを Blob として扱うが、PostgreSQL では Connection#createBlob() が使用できないため。
   */
  private[this] def updateBlob(id:Int, name:String, value:Option[Array[Byte]])(implicit _session:PostgresDriver.Backend#Session):Unit = {
    value match {
      case Some(binary) =>
        io.using(_session.prepareStatement("update intel.farms set " + name + "=? where id=?")){ stmt =>
          stmt.setBytes(1, binary)
          stmt.setInt(2, id)
          stmt.executeUpdate()
        }
      case None =>
        io.using(_session.prepareStatement("update intel.farms set " + name + "=null where id=?")){ stmt =>
          stmt.setInt(1, id)
          stmt.executeUpdate()
        }
    }
  }

  implicit class _Request(request:Request[MultipartFormData[Files.TemporaryFile]]){
    def getFile(name:String):Option[File] = request.body.file("icon").headOption.map { tmp =>
      val ext = new File(tmp.filename).getExtension
      val file = File.createTempFile(s"farm_${name}_", s".$ext")
      tmp.ref.moveTo(file, replace = true)
      file
    }.flatMap{ file =>
      if(file.length() == 0){
        file.delete()
        None
      } else Some(file)
    }
  }

  private[this] def asyncF(id:Int):Unit = play.api.db.slick.DB.withTransaction{ implicit _session =>
    updateFarm(id)
  }

  private[this] def updateFarm(id:Int)(implicit _session:PostgresDriver.Backend#Session):Unit = {
    Tables.Farms.filter{ _.id === id }.map{ _.externalKmlUrl }.firstOption match {
      case Some(None) =>
        logger.debug(s"外部URLではない")
      case Some(Some(url)) =>
        org.koiroha.bombaysapphire.geom.Region.fromKML(new URL(url)) match {
          case Some(region) =>
            val now = new Timestamp(System.currentTimeMillis())
            _session.withTransaction{
              Tables.FarmRegions.filter{ _.farmId === id }.delete
              Tables.FarmPortals.filter{ _.farmId === id }.delete
              // 領域定義の入れ替え
              region.parts.foreach{ poly =>
                sqlu"insert into intel.farm_regions(farm_id,region,created_at) values($id,${poly.toString}::polygon,$now)".first
              }
              // 所属ポータルの再設定
              sql"select distinct a.id from intel.portals as a, intel.farm_regions as b where b.farm_id=$id and point(a.late6/1e6,a.lnge6/1e6) <@ b.region".as[Int].list.foreach{ pid =>
                Tables.FarmPortals.map{ p => (p.farmId, p.portalId) }.insert((id, pid))
                logger.debug(s"ポータル $pid が所属しています")
              }
              // 代表地点の再計算
              // http://www.pasco.co.jp/recommend/word/word054/
              // TODO 点群の重心
            }
            logger.debug(s"領域を更新しました: $id")
          case None =>
            logger.warn(s"URLの内容が不正です: $url")
        }
      case None =>
        logger.warn(s"該当するファームが存在しません: $id")
    }
  }

}