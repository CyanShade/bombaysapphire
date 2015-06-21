/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire.agent.sentinel

import org.koiroha.bombaysapphire.agent.sentinel.Scenario.{Schedule, Interval}
import org.koiroha.bombaysapphire.agent.sentinel.xml._
import org.w3c.dom.{Document, Element}

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Scenario
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class Scenario(elem:Element){
	def account:String = elem.attr("account")
	def account_=(a:String):Unit = elem.attr("account", a)

	def waypoints = (elem \ "waypoints" \* "waypoint").flatMap{ e => WayPoint.parse(e.text) }
	def waypoints_=(wps:Iterable[WayPoint]):Unit = {
		val e = elem \ "waypoints"
		e.clear()
		wps.foreach{ wp =>
			val elem = e.getOwnerDocument.createElement("waypoint")
			elem.text = wp.toString
			e.appendChild(elem)
		}
	}

	def interval:Interval = {
		val Array(from, to) = elem.attr("interval").split(",", 2).map{ _.toInt }
		Interval(from, to)
	}
	def interval_=(i:Interval):Unit = elem.attr("interval", s"${i.from},${i.to}")

	def schedule = {
		elem.attr("schedule").split("\\s+", 5).toList match {
			case List(min, hour, date, month, weekday) => Schedule(min, hour, date, month, weekday)
		}
	}
	def schedule_=(s:Schedule):Unit = elem.attr("schedule", s"${s.minute} ${s.hour} ${s.date} ${s.month} ${s.weekday}")

}
object Scenario {
	case class Interval(from:Int, to:Int){
		def next():Int = from + (if(to > from) scala.util.Random.nextInt(to - from) else 0)
	}
	case class Schedule(minute:String, hour:String, date:String, month:String, weekday:String)
	def create(doc:Document):Element = {
		val elem = doc.createElement("session")
		elem.attr("account", "")
		elem.attr("interval", "10,10")
		elem.attr("schedule", "0 0 * * *")
		elem.appendChild(doc.createElement("waypoints"))
		elem
	}
}
