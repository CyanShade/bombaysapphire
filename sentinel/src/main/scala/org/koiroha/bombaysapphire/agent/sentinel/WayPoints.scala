package org.koiroha.bombaysapphire.agent.sentinel

import org.koiroha.bombaysapphire.BombaySapphire
import org.koiroha.bombaysapphire.geom.{LatLng, Latitude, Longitude, Rectangle, Region}


// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// WayPoints
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * 複数の観測地点を保持し哨戒経路を表す。
 *
 * @author Takami Torao
 */
case class WayPoints(waypoints:IndexedSeq[LatLng]) {
  def toStream:Stream[LatLng] = Stream(waypoints:_*)
}
object WayPoints {

  /**
   * 領域と緯度/経度方向のオフセットを指定して観測地点を決定する。
   * @param region 領域
   * @param latKm 緯度 [km]
   * @param lngKm 経度 [km]
   */
  /*
  def byOffset(region:Region, latKm:Double, lngKm:Double):WayPoints = {
    // 緯度方向の移動距離
    val latOffset:Latitude = BombaySapphire.latUnit * latKm
    // 経度方向の移動距離
    def lngOffset(lat:Double):Longitude = BombaySapphire.lngUnit(lat) * lngKm

    // 巡回対象の地点一覧
    val waypoints = {
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
    WayPoints(waypoints)
  }
  */

}
