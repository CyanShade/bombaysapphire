package org.koiroha.bombaysapphire.schema
// AUTO-GENERATED Slick data model
/** Stand-alone Slick data model for immediate use */
object Tables extends {
  val profile = scala.slick.driver.PostgresDriver
} with Tables

/** Slick data model trait for extension, choice of backend or usage in the cake pattern. (Make sure to initialize this late.) */
trait Tables {
  val profile: scala.slick.driver.JdbcProfile
  import profile.simple._
  import scala.slick.model.ForeignKeyAction
  // NOTE: GetResult mappers for plain SQL are only generated for tables where Slick knows how to map the types of all columns.
  import scala.slick.jdbc.{GetResult => GR}
  
  /** DDL for all tables. Call .create to execute. */
  lazy val ddl = Logs.ddl
  
  /** Entity class storing rows of table Logs
   *  @param id Database column id DBType(serial), AutoInc
   *  @param method Database column method DBType(varchar), Length(2147483647,true)
   *  @param log Database column log DBType(jsonb), Length(2147483647,false)
   *  @param createdAt Database column created_at DBType(timestamptz), Default(None) */
  case class LogsRow(id: Int, method: String, log: String, createdAt: Option[java.sql.Timestamp] = None)
  /** GetResult implicit for fetching LogsRow objects using plain SQL queries */
  implicit def GetResultLogsRow(implicit e0: GR[Int], e1: GR[String], e2: GR[Option[java.sql.Timestamp]]): GR[LogsRow] = GR{
    prs => import prs._
    LogsRow.tupled((<<[Int], <<[String], <<[String], <<?[java.sql.Timestamp]))
  }
  /** Table description of table logs. Objects of this class serve as prototypes for rows in queries. */
  class Logs(_tableTag: Tag) extends Table[LogsRow](_tableTag, Some("intel"), "logs") {
    def * = (id, method, log, createdAt) <> (LogsRow.tupled, LogsRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (id.?, method.?, log.?, createdAt).shaped.<>({r=>import r._; _1.map(_=> LogsRow.tupled((_1.get, _2.get, _3.get, _4)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))
    
    /** Database column id DBType(serial), AutoInc */
    val id: Column[Int] = column[Int]("id", O.AutoInc)
    /** Database column method DBType(varchar), Length(2147483647,true) */
    val method: Column[String] = column[String]("method", O.Length(2147483647,varying=true))
    /** Database column log DBType(jsonb), Length(2147483647,false) */
    val log: Column[String] = column[String]("log", O.Length(2147483647,varying=false))
    /** Database column created_at DBType(timestamptz), Default(None) */
    val createdAt: Column[Option[java.sql.Timestamp]] = column[Option[java.sql.Timestamp]]("created_at", O.Default(None))
  }
  /** Collection-like TableQuery object for table Logs */
  lazy val Logs = new TableQuery(tag => new Logs(tag))
}