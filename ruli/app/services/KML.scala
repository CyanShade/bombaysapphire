package services

import java.io.{InputStream, FileInputStream, File}
import java.util.zip.ZipFile

import org.slf4j.LoggerFactory
import org.xml.sax.InputSource

import scala.xml.XML
import scala.collection.JavaConversions._


// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// KML
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
object KML {
  private[this] val logger = LoggerFactory.getLogger(getClass.getName)

  /** KML または KMZ 形式のファイルから領域の読み込み */
  def fromFile(file:File):Area = {
    if(file.getName.endsWith(".kml")) {
      io.using(new FileInputStream(file)){ in => fromKML(file.toString, in) }
    } else if(file.getName.endsWith(".kmz")) {
      io.using(new ZipFile(file)){ zfile =>
        zfile.entries().collectFirst {
          case e if e.getName == "doc.kml" =>
            fromKML(file.toString + "#doc.kml", zfile.getInputStream(e))
        } match {
          case Some(e) => e
          case None =>
            logger.error(s"KMZファイルフォーマットが不正です: ${file.getName}")
            throw new IllegalArgumentException(file.getName)
        }
      }
    } else {
      logger.error(s"未対応のファイル拡張子です: ${file.getName}")
      throw new IllegalArgumentException(file.getName)
    }
  }

  /** KML 形式のストリームから領域の読み込み */
  def fromInputStream(in:InputStream):Area = fromKML("", in)

  /** KML 形式のストリームから領域の読み込み */
  private[this] def fromKML(systemId:String, in:InputStream):Area = {

    val is = new InputSource(systemId)
    is.setByteStream(in)

    val doc = XML.load(is)
    Area((doc \\ "Placemark").flatMap { placemark =>

      // KML から多角形の座標を取得
      (placemark \ "Polygon").map { polygon =>
        (polygon \ "outerBoundaryIs" \ "LinearRing" \ "coordinates").text.trim()
          .split("\\s+").map {_.split(",")}.filter {_.length > 0}.map {
          case Array(lng, lat, _) => (lat.toDouble, lng.toDouble)
          case Array(lng, lat) => (lat.toDouble, lng.toDouble)      // altitude 省略時
          case unexpected =>
            logger.error(s"unexpected coordinate values: $unexpected")
            throw new IllegalArgumentException()
        }.toSeq
      }.map{ p => Polygon(p) }
    })
  }

  case class Polygon(points:Seq[(Double,Double)])
  case class Area(polygons:Seq[Polygon])
}
