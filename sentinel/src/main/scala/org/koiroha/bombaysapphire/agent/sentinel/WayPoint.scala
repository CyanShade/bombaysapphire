/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire.agent.sentinel

import java.io.File
import java.net.URI

import org.koiroha.bombaysapphire.{BombaySapphire, _}
import org.koiroha.bombaysapphire.geom.{Dimension, Region}
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success, Try}

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// WayPoint
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
sealed abstract class WayPoint() {
	def toString:String
}
object WayPoint {
	private val logger = LoggerFactory.getLogger(classOf[WayPoint])
	def parse(text:String):Option[WayPoint] = text.split(":", 2).map{ _.trim } match {
		case Array("point", point) =>
			point.split(",", 2) match {
				case Array(lat, lng) => Try{ FixedPoint(lat.trim().toDouble, lng.trim().toDouble) }.toOption
				case _ => None
			}
		case Array("portal", point) =>
			point.split(",", 2) match {
				case Array(lat, lng) => Try{ Portal(lat.trim().toDouble, lng.trim().toDouble) }.toOption
				case _ => None
			}
		case Array("keyword", keyword) => Some(Keyword(keyword))
		case Array("kml", url) => Some(KML(url))
		case Array("rect", rect) =>
			rect.split(",", 4) match {
				case Array(north, east, south, west) =>
					Try{ Rectangle(north.toDouble, east.toDouble, south.toDouble, west.toDouble) }.toOption
				case _ => None
			}
		case a =>
			logger.warn(s"unknown waypoint: ${a.mkString(":")}")
			None
	}
}

sealed abstract class Area extends WayPoint {
	def toWayPoints(unit:Dimension):Seq[WayPoint]
}
object Area {
	import org.koiroha.bombaysapphire.BombaySapphire._
	def grids(north:Double, east:Double, south:Double, west:Double, unit:Dimension):Seq[FixedPoint] = {
		val Dimension(lngKM, latKM) = unit
		for{
			lat <- (south + latUnit      * latKM / 2) to (north + latUnit      * latKM / 2) by (latUnit      * latKM)
			lng <- (west  + lngUnit(lat) * lngKM / 2) to (east  + lngUnit(lat) * lngKM / 2) by (lngUnit(lat) * lngKM)
		} yield FixedPoint(lat, lng)
	}
}

/** 定点 */
case class FixedPoint(lat:Double, lng:Double) extends WayPoint {
	override def toString = s"point:$lat,$lng"
}

/** ポータル */
case class Portal(lat:Double, lng:Double) extends WayPoint {
	override def toString = s"portal:$lat,$lng"
}

/** キーワード */
case class Keyword(keyword:String) extends WayPoint {
	override def toString = s"keyword:$keyword"
}

/** KML が示す領域 */
case class KML(url:String) extends Area {
	override def toString = s"kml:$url"
	def regions:Seq[Region] = {
		import org.koiroha.bombaysapphire.{KML => K}
		Try{ URI.create(url) } match {
			case Success(uri) if uri.isAbsolute =>
				if(uri.getScheme == "file"){
					K.toRegion(new File(uri))
				} else {
					io.using(URI.create(url).toURL.openStream()) { in =>
						K.toRegion(in)
					}
				}
			case Success(uri) => K.toRegion(new File(url))
			case Failure(ex) => K.toRegion(new File(url))
		}
	}
	def toWayPoints(unit:Dimension):Seq[WayPoint] = {
		val Dimension(lngKM, latKM) = unit
		regions.flatMap{ r =>
			val rect = r.rectangle
			val g = Area.grids(rect.north, rect.east, rect.south, rect.west, unit)
			g.filter{ p =>
				val dlat2 = BombaySapphire.latUnit * latKM / 2
				val dlng2 = BombaySapphire.lngUnit(p.lat) * lngKM / 2
				r.intersects(geom.Rectangle(p.lat + dlat2, p.lng + dlng2, p.lat - dlat2, p.lng - dlng2))
			}
		}
	}
}

/** 矩形が示す領域 */
case class Rectangle(north:Double, east:Double, south:Double, west:Double) extends Area {
	override def toString = s"rect:$north,$east,$south,$west"
	def toWayPoints(unit:Dimension):Seq[WayPoint] = Area.grids(north, east, south, west, unit)
}
