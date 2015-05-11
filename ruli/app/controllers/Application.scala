package controllers

import java.sql.Timestamp
import java.text.{DateFormat, SimpleDateFormat}

import org.slf4j.LoggerFactory
import play.api.mvc._

import scala.util.Try

object Application extends Controller {
  private[this] val logger = LoggerFactory.getLogger(getClass)

  def index = Action {
    Ok(views.html.portals())
  }

  def regions = Action {
    Ok(views.html.index("Your new application is ready."))
  }

  class BadRequest(msg:String) extends Exception(msg)

  def toTimestamp(value:String):Option[Timestamp] = (if(value.matches("\\d+")) {
    // 2014-12-30 の様な表記が -1 月と解釈されるようなので別評価とする
    Seq("yyyyMMddHHmmss", "yyyyMMddHHmm", "yyyyMMddHH", "yyyyMMdd")
  } else {
    Seq(
      "yyyy/MM/dd H:mm:ss", "yyyy-MM-dd H:mm:ss",
      "yyyy/MM/dd H:mm", "yyyy-MM-dd H:mm",
      "yyyy/MM/dd H", "yyyy-MM-dd H",
      "yyyy/MM/dd", "yyyy-MM-dd")
  }).flatMap{ fmt =>
    val df = new SimpleDateFormat(fmt)
    //    df.setTimeZone(Context.Locale.timezone)
    Try{ new Timestamp(df.parse(value).getTime) }.toOption
  }.headOption.map{ x =>
    logger.debug(s"timestamp: $value -> ${DateFormat.getDateTimeInstance.format(x)}")
    x
  }

  def toString(value:Timestamp):String = {
    val df = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT)
    //    df.setTimeZone(Context.Locale.timezone)
    df.format(value)
  }

}