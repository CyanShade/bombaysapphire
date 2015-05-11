/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package models.farm

import java.sql.Timestamp

import models.Tables
import models.geom.Area
import scala.slick.driver.PostgresDriver.simple._

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Farm
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
case class Farm(id:Option[Int], parentId:Option[Int], name:String, area:Area, latestLogId:Option[Int], description:String, formattedDescription:String, createdAt:Long, updatedAt:Long) {
  /*
	  /** Database column id DBType(serial), AutoInc, PrimaryKey */
    val id: Column[Int] = column[Int]("id", O.AutoInc, O.PrimaryKey)
    /** Database column parent DBType(int4), Default(None) */
    val parent: Column[Option[Int]] = column[Option[Int]]("parent", O.Default(None))
    /** Database column name DBType(varchar), Length(2147483647,true) */
    val name: Column[String] = column[String]("name", O.Length(2147483647,varying=true))
    /** Database column kml DBType(bytea) */
    val kml: Column[java.sql.Blob] = column[java.sql.Blob]("kml")
    /** Database column latest_log DBType(int4), Default(None) */
    val latestLog: Column[Option[Int]] = column[Option[Int]]("latest_log", O.Default(None))
    /** Database column description DBType(text), Length(2147483647,true) */
    val description: Column[String] = column[String]("description", O.Length(2147483647,varying=true))
    /** Database column formatted_description DBType(text), Length(2147483647,true) */
    val formattedDescription: Column[String] = column[String]("formatted_description", O.Length(2147483647,varying=true))
    /** Database column created_at DBType(timestamp) */
    val createdAt: Column[java.sql.Timestamp] = column[java.sql.Timestamp]("created_at")
    /** Database column updated_at DBType(timestamp) */
    val updatedAt: Column[java.sql.Timestamp] = column[java.sql.Timestamp]("updated_at")
	*/
}
object Farm {
  def apply(farms:List[Tables.Farms#TableElementType]):Seq[Farm] = farms.map{ farm =>
    Farm(Some(farm.id), farm.parent, farm.name, Area.fromString(farm.area).get, farm.latestLog, farm.description, farm.formattedDescription, farm.createdAt.getTime, farm.updatedAt.getTime)
  }

  def save(id:Option[Int], parentId:Option[Int], name:String, area:Option[Area],
           description:String, formattedDescription:String)(implicit _session:Session):Unit = {
    val now = new Timestamp(System.currentTimeMillis())
    id match {
      case None =>
        Tables.Farms.map{ c => (c.name, c.description, c.formattedDescription, c.area, c.createdAt, c.updatedAt) } += (name, description, formattedDescription, area.get.toString, now, now)
      case Some(i) =>
        Tables.Farms.filter{ _.id === i }.map{ c => (c.name, c.description, c.formattedDescription, c.area, c.updatedAt) }.update((name, description, formattedDescription, area.get.toString, now))
      }
  }
}
