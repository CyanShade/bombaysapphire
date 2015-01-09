package org.koiroha.bombaysapphire.agent

import org.koiroha.bombaysapphire.BombaySapphire
import org.koiroha.bombaysapphire.geom.{LatLng, Latitude, Longitude, Rectangle, Region}
import org.slf4j.LoggerFactory

import scala.concurrent.Await
import scala.concurrent.duration.Duration


// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// WayPoints
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * 哨戒順序。
 * @author Takami Torao
 */
trait WayPoints {
  def by(area:Region):WayPoints.Iterator
  def toString:String
}
object WayPoints {
  private[this] val logger = LoggerFactory.getLogger(classOf[WayPoints])

  trait Iterator {
    def next():LatLng
    def remains:Int
  }

  class ByOffset(latKm:Double, lngKm:Double) extends WayPoints {
    /** 緯度方向の移動距離 */
    private[this] val latOffset:Latitude = BombaySapphire.latUnit * latKm
    /** 経度方向の移動距離 */
    private[this] def lngOffset(lat:Double):Longitude = BombaySapphire.lngUnit(lat) * lngKm
    def by(region:Region):Iterator = new Iterator() {
      /** 巡回対象の地点一覧 */
      private[this] var waypoints = {
        // 矩形領域から巡回対象のセルを算出
        val rect = region.rectangle
        val ps = for {
          lat <- rect.south to rect.north by latOffset
          lng <- rect.west to rect.east by lngOffset(lat)
        } yield LatLng(lat + latOffset / 2, lng + lngOffset(lat) / 2)
        // 巡回対象と実際に重なる部分だけにフィルタリング
        ps.filter { case LatLng(lat, lng) =>
          val r = Rectangle(lat + latOffset / 2, lng + lngOffset(lat) / 2, lat - latOffset / 2, lng - lngOffset(lat) / 2)
          region.intersects(r)
        }
      }
      def next():LatLng = {
        val head = waypoints.head
        waypoints = waypoints.tail
        head
      }
      def remains:Int = waypoints.size
    }
    override def toString = f"offset($latKm%.2f[km], $lngKm%.2f[km])"
  }

  /**
   * 現在の DB に保存されている tile_key に基づく移動方法。
   * 領域内の全ての tile_key が取得済みであることを想定している場合に使用できる。
   */
  class ByTileKeys(api:GarudaAPI, ignoreable:(String)=>Boolean) extends WayPoints {
    def by(area:Region):Iterator = new Iterator {
      private[this] var waypoints = Await.result(api.tileKeys(area.rectangle), Duration.Inf)
      def next():LatLng = {
        val head = waypoints.head
        waypoints = waypoints.tail
        head._2.center
      }
      def remains: Int = {
        waypoints = waypoints.filterNot { k => ignoreable(k._1) }
        waypoints.size
      }
    }
    /*
    def allCenterPositions:Seq[(Double,Double)] = Context.Database.withSession { implicit session =>
      logger.debug("finding tile_keys")
      Tables.Portals.groupBy{ _.tileKey }
        .map{ case (tileKey, c) => (tileKey, c.map{ _.late6 }.max, c.map{ _.late6 }.min, c.map{ _.lnge6 }.max, c.map{ _.lnge6 }.min) }
        .list
        .map { case (tileKey, lat0, lat1, lng0, lng1) => ((lat1.get+lat0.get)/2/1e6, (lng1.get+lng0.get)/2/1e6) }
    }
    */
    override def toString = "tile_keys"
  }

}