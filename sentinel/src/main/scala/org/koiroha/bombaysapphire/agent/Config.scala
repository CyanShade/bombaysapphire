package org.koiroha.bombaysapphire.agent

import java.io.File
import java.net.URI

import org.koiroha.bombaysapphire.KML
import org.koiroha.bombaysapphire.agent.WayPoints.{ByOffset, ByTileKeys}
import org.koiroha.bombaysapphire.geom.{Rectangle, Region}
import org.slf4j.LoggerFactory

import scala.concurrent.Await
import scala.concurrent.duration.Duration

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Config
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
object Config {

}
case class Config(config:Map[String,String]) {
  private[this] val logger = LoggerFactory.getLogger(classOf[Config])

  /** Intel Map のスクリーンサイズ */
  val mapSize = (5120, 2880)
  /** 縮小率 */
  val scale = 800.0 / mapSize._1
  /** Intel Map 表示領域の物理サイズ (800x450) */
  val viewSize = ((mapSize._1 * scale).toInt, (mapSize._2 * scale).toInt)
  /** z=17 において 100[m]=206[pixel] (Retina), 1kmあたりのピクセル数 */
  private[this] val unitKmPixels = 206 * (1000.0 / 100)
  /** 表示領域が示す縦横の距離[km] (実際はマージンがあるが) */
  val areaSize = (mapSize._1 / unitKmPixels, mapSize._2 / unitKmPixels)
  logger.info(f"スクリーンの実際の距離: ${areaSize._1}%.2fkm × ${areaSize._2}%.2fkm")

  /** 取得したデータの送信先 */
  lazy val garuda = new GarudaAPI(URI.create(config("garuda")).toURL)

  /** 哨戒領域 */
  lazy val patrolRegion:Region = getNameValues("patrol.region") match {
    case Some(("rect", area)) =>
      area match {
        case Seq(name, north, east, south, west) =>
          Region(name, Seq(Rectangle(north.toDouble, east.toDouble, south.toDouble, west.toDouble)))
        case unexpected =>
          logger.error(s"'rect' のパラメータが不正です. (name,north,east,south,west) を指定してください")
          throw new IllegalArgumentException(s"'rect' should be (north,east,south,west): (${unexpected.mkString(",")})")
      }
    case Some(("server", district)) =>
      val f = district match {
        case Seq(country, state, city) => garuda.administrativeDistrict(country, state, city)
        case Seq(country, state) => garuda.administrativeDistrict(country, state, "")
        case Seq(country) => garuda.administrativeDistrict(country, "", "")
      }
      Await.result(f, Duration.Inf) match {
        case Some(reg) => reg
        case None =>
          logger.error(s"指定された行政区はサーバで定義されていません")
          throw new IllegalStateException(s"specified administrative district is not defined")
      }
    case Some(("local", district)) =>
      val Seq(file, country, state, city) = (district ++ Seq("", "", "")).take(4)
      KML.fromFile(new File(file).getCanonicalFile).find{ ad =>
        (country == "") || (ad.country == country && ad.state == state && ad.city == city)
      } match {
        case Some(reg) => reg.toRegion
        case None =>
          logger.error(s"指定された行政区はKMZファイル内に定義されていません: $country, $state, $city")
          throw new IllegalStateException(s"specified administrative district is not defined")
      }
    case Some((unexpected, _)) =>
      logger.error(s"巡回領域の指定が不正です: $unexpected")
      throw new IllegalStateException()
    case None =>
      logger.error(s"巡回領域が定義されていません")
      throw new IllegalStateException()
  }

  /** 巡回区域 */
  def newWayPoints(tileKeysIgnoreable:(String)=>Boolean):WayPoints = getNameValues("patrol.algorithm") match {
    case Some(("tile_keys", Seq())) => new ByTileKeys(garuda, tileKeysIgnoreable)
    case Some(("all", Seq())) => new ByOffset(areaSize._1, areaSize._2)
    case Some(("offset", Seq(latkm,lngkm))) => new ByOffset(latkm.toDouble, lngkm.toDouble)
    case Some((name, _)) =>
      logger.error(s"哨戒方法を認識できません: $name")
      throw new IllegalArgumentException(name)
    case None =>
      logger.warn(s"patrol.algorithm が設定されていません. 全てのエリアを探索します.")
      new ByOffset(areaSize._1, areaSize._2)
  }

  /** Intel アクセス用のアカウント名 */
  lazy val account = config("account")
  /** Intel アクセス用アカウントのパスワード */
  lazy val password = config("password")
  /** 表示間隔[秒] */
  lazy val intervalSeconds = config.get("interval").map{ _.toLong }.getOrElse(60 * 1000L)
  /** User-Agent */
  lazy val userAgent = config.getOrElse("user-agent",
    "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/39.0.2171.95 Safari/537.36")
  // Mozilla/5.0 (Windows NT 6.1; Win64; x64) AppleWebKit/537.44 (KHTML, like Gecko) JavaFX/8.0 Safari/537.44
  // Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/39.0.2171.95 Safari/537.36
  lazy val overLimit = config.get("overlimit.ignore").map{ _.toInt }.getOrElse(5)

  private[this] val NamePattern = """([a-zA-Z0-9\._]+)""".r
  private[this] val NameValuesPattern = """([a-zA-Z0-9\._]+)\s*\(\s*(.*)\s*\)""".r
  private[this] def getNameValues(key:String):Option[(String,Seq[String])] = config.get(key).map{ value =>
    value.trim() match {
      case NamePattern(name) => (name.trim, Seq())
      case NameValuesPattern(name, args) =>
        (name, args.split("\\s*,\\s*").map{ _.trim }.toSeq)
    }
  }
}
