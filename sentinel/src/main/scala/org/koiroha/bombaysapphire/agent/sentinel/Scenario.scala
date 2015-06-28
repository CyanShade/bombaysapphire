/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire.agent.sentinel

import it.sauronsoftware.cron4j.Scheduler
import org.koiroha.bombaysapphire.agent.sentinel.Scenario.{_Interval, _Schedule}
import org.koiroha.bombaysapphire.agent.sentinel.xml._
import org.koiroha.bombaysapphire.geom.Dimension
import org.slf4j.LoggerFactory
import org.w3c.dom.Element

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Scenario
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class Scenario(elem:Element){

	/** このシナリオの WayPoint */
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
	def allWaypoints(dim:Dimension):Seq[WayPoint] = waypoints.flatMap {
		case area: Area => area.toWayPoints(dim)
		case wp:WayPoint => Seq(wp)
	}

	object interval {
		private def apply():_Interval = {
			elem.attr("interval").split(",", 2).map{ _.toInt } match {
			  case Array(from, to) => _Interval(from, to)
				case Array(t) => _Interval(t, t)
			}
		}
		private def update(i:_Interval):Unit = elem.attr("interval", s"${i.from},${i.to}")
		def from = apply().from
		def from_=(value:Int) = update(apply().copy(from=value))
		def to = apply().to
		def to_=(value:Int) = update(apply().copy(to=value))
		def next():Int = from + (if(to > from) scala.util.Random.nextInt(to - from) else 0)
		def average:Double = {
			val _Interval(from, to) = apply()
			(from + to) / 2.0
		}
	}

	object schedule {
		private def apply() = {
			elem.attr("schedule").split("\\s+", 6).toList match {
				case List(min, hour, date, month, weekday) => _Schedule(false, min, hour, date, month, weekday)
				case List(enabled, min, hour, date, month, weekday) => _Schedule(enabled.toBoolean, min, hour, date, month, weekday)
			}
		}
		private def update(s:_Schedule):Unit = {
			elem.attr("schedule", s"${s.enabled} ${s.minute} ${s.hour} ${s.date} ${s.month} ${s.weekday}")
		}
		def enabled = apply().enabled
		def enabled_=(value:Boolean) = update(apply().copy(enabled=value))
		def minute = apply().minute
		def minute_=(value:String) = update(apply().copy(minute=value))
		def hour = apply().hour
		def hour_=(value:String) = update(apply().copy(hour=value))
		def date = apply().date
		def date_=(value:String) = update(apply().copy(date=value))
		def month = apply().month
		def month_=(value:String) = update(apply().copy(month=value))
		def weekday = apply().weekday
		def weekday_=(value:String) = update(apply().copy(weekday=value))
		def schedule(f: =>Unit):String = {
			val tm = s"$minute $hour $date $month $weekday"
			Scenario.scheduler.schedule(tm, new Runnable {
				override def run(): Unit = f
			})
		}
		def deschedule(s:String):Unit = {
			Scenario.scheduler.deschedule(s)
		}
	}

}
object Scenario {
	private[Scenario] val logger = LoggerFactory.getLogger(classOf[Scenario])
	case class _Interval(from:Int, to:Int)
	case class _Schedule(enabled:Boolean, minute:String, hour:String, date:String, month:String, weekday:String)

	val scheduler = new Scheduler()
	scheduler.setDaemon(true)
	scheduler.start()
}
