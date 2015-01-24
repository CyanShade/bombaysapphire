/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire

import java.awt.geom.{Path2D, Point2D, Rectangle2D}

import scala.language.reflectiveCalls

package object io {
	def using[T <: {def close():Unit},R](r:T)(exec:(T)=>R):R = try {
		exec(r)
	} finally {
		r.close()
	}
}

package object geom {
	type Latitude = Double
	type Longitude = Double

	case class LatLng(latitude: Latitude, longitude: Longitude) {
		assert(latitude >= -90 && latitude <= 90, s"$latitude overflow")
		assert(longitude >= -180 && longitude <= 180, s"$longitude overflow")
		def this(latE6: Int, lngE6: Int) = this(latE6 / 1e6, lngE6 / 1e6)
	}

	trait Shape {
		def contains(point: LatLng): Boolean = contains(point.latitude, point.longitude)

		def contains(lat: Latitude, lng: Longitude): Boolean

		def intersects(rect: Rectangle): Boolean

		def rectangle: Rectangle
	}

	case class Polygon(points: Seq[LatLng]) extends Shape {
		assert(points.size >= 3)
		/** (lat,lng) の多角形 */
		private[this] val polygon = locally {
			val path = new Path2D.Double()
			path.moveTo(points(0).latitude, points(0).longitude)
			points.foreach { case LatLng(lat, lng) => path.lineTo(lat, lng)}
			if (points(0) != points(points.size - 1)) {
				path.lineTo(points(0).latitude, points(0).longitude)
			}
			path
		}

		def contains(lat: Latitude, lng: Longitude): Boolean = polygon.contains(new Point2D.Double(lat, lng))

		def intersects(rect: Rectangle): Boolean = this.polygon.intersects(rect.rect)

		lazy val rectangle: Rectangle = {
			val rect = polygon.getBounds2D
			Rectangle(rect.getX + rect.getWidth, rect.getY + rect.getHeight, rect.getX, rect.getY)
		}
	}

	case class Rectangle(north: Latitude, east: Longitude, south: Latitude, west: Longitude) extends Shape {
		assert(north >= -90 && north <= 90, s"$north overflow")
		assert(east >= -180 && east <= 180, s"$east overflow")
		assert(south >= -90 && south <= 90, s"$south overflow")
		assert(west >= -180 && west <= 180, s"$west overflow")
		assert(north >= south)
		assert(east >= west)

		/** x:緯度, y:経度 の矩形 */
		private[bombaysapphire] val rect = new Rectangle2D.Double(south, west, north - south, east - west)

		def contains(lat: Latitude, lng: Longitude): Boolean = rect.contains(new Point2D.Double(lat, lng))

		def intersects(r: Rectangle): Boolean = this.rect.intersects(r.rect)

		def rectangle = this

		def union(r: Rectangle): Rectangle = {
			val rx = r.rect.createUnion(this.rect)
			Rectangle(rx.getX + rx.getWidth, rx.getY + rx.getHeight, rx.getX, rx.getY)
		}

		lazy val center: LatLng = LatLng((north + south) / 2.0, (east + west) / 2.0)
	}

	case class Region(name: String, shapes: Seq[Shape]) extends Shape {
		def contains(lat: Latitude, lng: Longitude): Boolean = shapes.exists {
			_.contains(lat, lng)
		}

		def intersects(rect: Rectangle): Boolean = shapes.exists {
			_.intersects(rect)
		}

		def rectangle: Rectangle = shapes.map {
			_.rectangle
		}.reduceLeft { (a, b) => a.union(b)}
	}

	case class AdministrativeDistrict(country: String, state: String, city: String, shapes: Seq[Shape]) {
		val toRegion = Region(s"$country $state $city", shapes)
	}

}
