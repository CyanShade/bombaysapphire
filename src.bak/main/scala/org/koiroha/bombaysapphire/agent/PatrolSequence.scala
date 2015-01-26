/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire.agent

import org.koiroha.bombaysapphire.Context
import org.koiroha.bombaysapphire.schema.Tables
import org.slf4j.LoggerFactory
import scala.slick.driver.PostgresDriver.simple._

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// PatrolSequence
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
trait PatrolSequence {
	/** 次の表示位置を参照 */
	def next:(Double,Double)
	/** 残りの表示位置数を参照 */
	def remains:Int
}
object PatrolSequence {
	private[PatrolSequence] val logger = LoggerFactory.getLogger(classOf[PatrolSequence])

	/** 地球の外周[km] */
	private[this] val earthRound = 40000.0
	/** 1kmあたりの緯度 */
	private[this] val latUnit = 360.0 / earthRound
	/** 1kmあたりの経度 */
	private[this] def lngUnit(lat:Double):Double = latUnit * math.cos(lat / 360 * 2 * math.Pi)

	/**
	 * オフセットによる哨戒順序。
	 * @param latKm 南北方向の移動距離
	 * @param lngKm 東西方向の移動距離
	 */
	case class ByOffset(latKm:Double, lngKm:Double) extends PatrolSequence {
		private[this] val latOffset = latUnit * latKm / 2
		private[this] def lngOffset(lat:Double) = lngUnit(lat) * lngKm / 2
		private[this] var _remains = locally {
			def next(lat:Double, lng:Double):Stream[(Double,Double)] = {
				val n = if(lng + lngOffset(lat) <= Context.Region.east){
					(lat, lng + lngOffset(lat) * 2)
				} else if(lat - latOffset >= Context.Region.south){
					(lat - latOffset * 2, Context.Region.west)
				} else {
					(Double.NaN, Double.NaN)
				}
				(lat, lng) #:: next(n._1, n._2)
			}
			next(Context.Region.north + latOffset, Context.Region.west + lngOffset(Context.Region.north)).takeWhile{ ! _._1.isNaN }
		}.filter{ case (lat, lng) =>
			val lat0 = lat - latOffset
			val lat1 = lng + latOffset
			val lng0 = lat - lngOffset(lat)
			val lng1 = lng + lngOffset(lat)
			Context.Region.overlap(lat0, lat1, lng0, lng1)
		}
		def next = {
			val head = _remains.head
			_remains = _remains.tail
			head
		}
		def remains:Int = _remains.size
		override def toString = f"offset($latKm%.2f[km], $lngKm%.2f[km])"
	}

	/**
	 * 現在の DB に保存されている tile_key に基づく移動方法。
	 * 領域内の全ての tile_key が取得済みであることを想定している場合に使用できる。
	 */
	class ByTileKeys(proxy:ProxyServer) extends PatrolSequence {
		private[this] var _remainings = Context.Database.withSession { implicit session =>
			logger.debug("finding tile_keys")
			Tables.Portals.groupBy{ _.tileKey }
				.map{ case (tileKey, c) => (tileKey, c.map{ _.id }.size, c.map{ _.late6 }.max, c.map{ _.late6 }.min, c.map{ _.lnge6 }.max, c.map{ _.lnge6 }.min) }
				.sortBy { _._2.desc }
				.list
				.map { case (tileKey, _, lat0, lat1, lng0, lng1) => (tileKey, (lat1.get+lat0.get)/2/1e6, (lng1.get+lng0.get)/2/1e6) }
		}
		def next = {
			val head = _remainings.head
			_remainings = _remainings.tail.filterNot{ tk => proxy.tileKeys.contains(tk._1) }
			(head._2, head._3)
		}
		def remains = _remainings.size
		override def toString = "tile_keys"
	}

}