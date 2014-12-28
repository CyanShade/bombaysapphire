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
  lazy val ddl = Agents.ddl ++ Geohash.ddl ++ Logs.ddl ++ Portals.ddl ++ PortalStateLogs.ddl
  
  /** Entity class storing rows of table Agents
   *  @param id Database column id DBType(serial), AutoInc, PrimaryKey
   *  @param name Database column name DBType(varchar), Length(2147483647,true)
   *  @param team Database column team DBType(bpchar), Length(1,false)
   *  @param createdAt Database column created_at DBType(timestamp) */
  case class AgentsRow(id: Int, name: String, team: String, createdAt: java.sql.Timestamp)
  /** GetResult implicit for fetching AgentsRow objects using plain SQL queries */
  implicit def GetResultAgentsRow(implicit e0: GR[Int], e1: GR[String], e2: GR[java.sql.Timestamp]): GR[AgentsRow] = GR{
    prs => import prs._
    AgentsRow.tupled((<<[Int], <<[String], <<[String], <<[java.sql.Timestamp]))
  }
  /** Table description of table agents. Objects of this class serve as prototypes for rows in queries. */
  class Agents(_tableTag: Tag) extends Table[AgentsRow](_tableTag, Some("intel"), "agents") {
    def * = (id, name, team, createdAt) <> (AgentsRow.tupled, AgentsRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (id.?, name.?, team.?, createdAt.?).shaped.<>({r=>import r._; _1.map(_=> AgentsRow.tupled((_1.get, _2.get, _3.get, _4.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))
    
    /** Database column id DBType(serial), AutoInc, PrimaryKey */
    val id: Column[Int] = column[Int]("id", O.AutoInc, O.PrimaryKey)
    /** Database column name DBType(varchar), Length(2147483647,true) */
    val name: Column[String] = column[String]("name", O.Length(2147483647,varying=true))
    /** Database column team DBType(bpchar), Length(1,false) */
    val team: Column[String] = column[String]("team", O.Length(1,varying=false))
    /** Database column created_at DBType(timestamp) */
    val createdAt: Column[java.sql.Timestamp] = column[java.sql.Timestamp]("created_at")
    
    /** Uniqueness Index over (name) (database name agents_idx) */
    val index1 = index("agents_idx", name, unique=true)
    /** Uniqueness Index over (name) (database name agents_name_key) */
    val index2 = index("agents_name_key", name, unique=true)
  }
  /** Collection-like TableQuery object for table Agents */
  lazy val Agents = new TableQuery(tag => new Agents(tag))
  
  /** Entity class storing rows of table Geohash
   *  @param geohash Database column geohash DBType(bpchar), PrimaryKey, Length(5,false)
   *  @param late5 Database column late5 DBType(int4)
   *  @param lnge5 Database column lnge5 DBType(int4)
   *  @param country Database column country DBType(bpchar), Length(2,false)
   *  @param state Database column state DBType(varchar), Length(2147483647,true)
   *  @param city Database column city DBType(varchar), Length(2147483647,true) */
  case class GeohashRow(geohash: String, late5: Int, lnge5: Int, country: String, state: String, city: String)
  /** GetResult implicit for fetching GeohashRow objects using plain SQL queries */
  implicit def GetResultGeohashRow(implicit e0: GR[String], e1: GR[Int]): GR[GeohashRow] = GR{
    prs => import prs._
    GeohashRow.tupled((<<[String], <<[Int], <<[Int], <<[String], <<[String], <<[String]))
  }
  /** Table description of table geohash. Objects of this class serve as prototypes for rows in queries. */
  class Geohash(_tableTag: Tag) extends Table[GeohashRow](_tableTag, Some("intel"), "geohash") {
    def * = (geohash, late5, lnge5, country, state, city) <> (GeohashRow.tupled, GeohashRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (geohash.?, late5.?, lnge5.?, country.?, state.?, city.?).shaped.<>({r=>import r._; _1.map(_=> GeohashRow.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))
    
    /** Database column geohash DBType(bpchar), PrimaryKey, Length(5,false) */
    val geohash: Column[String] = column[String]("geohash", O.PrimaryKey, O.Length(5,varying=false))
    /** Database column late5 DBType(int4) */
    val late5: Column[Int] = column[Int]("late5")
    /** Database column lnge5 DBType(int4) */
    val lnge5: Column[Int] = column[Int]("lnge5")
    /** Database column country DBType(bpchar), Length(2,false) */
    val country: Column[String] = column[String]("country", O.Length(2,varying=false))
    /** Database column state DBType(varchar), Length(2147483647,true) */
    val state: Column[String] = column[String]("state", O.Length(2147483647,varying=true))
    /** Database column city DBType(varchar), Length(2147483647,true) */
    val city: Column[String] = column[String]("city", O.Length(2147483647,varying=true))
  }
  /** Collection-like TableQuery object for table Geohash */
  lazy val Geohash = new TableQuery(tag => new Geohash(tag))
  
  /** Entity class storing rows of table Logs
   *  @param id Database column id DBType(int8), PrimaryKey
   *  @param method Database column method DBType(varchar), Length(2147483647,true)
   *  @param content Database column content DBType(jsonb), Length(2147483647,false)
   *  @param request Database column request DBType(varchar), Length(2147483647,true)
   *  @param response Database column response DBType(varchar), Length(2147483647,true)
   *  @param createdAt Database column created_at DBType(timestamp) */
  case class LogsRow(id: Long, method: String, content: String, request: String, response: String, createdAt: java.sql.Timestamp)
  /** GetResult implicit for fetching LogsRow objects using plain SQL queries */
  implicit def GetResultLogsRow(implicit e0: GR[Long], e1: GR[String], e2: GR[java.sql.Timestamp]): GR[LogsRow] = GR{
    prs => import prs._
    LogsRow.tupled((<<[Long], <<[String], <<[String], <<[String], <<[String], <<[java.sql.Timestamp]))
  }
  /** Table description of table logs. Objects of this class serve as prototypes for rows in queries. */
  class Logs(_tableTag: Tag) extends Table[LogsRow](_tableTag, Some("intel"), "logs") {
    def * = (id, method, content, request, response, createdAt) <> (LogsRow.tupled, LogsRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (id.?, method.?, content.?, request.?, response.?, createdAt.?).shaped.<>({r=>import r._; _1.map(_=> LogsRow.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))
    
    /** Database column id DBType(int8), PrimaryKey */
    val id: Column[Long] = column[Long]("id", O.PrimaryKey)
    /** Database column method DBType(varchar), Length(2147483647,true) */
    val method: Column[String] = column[String]("method", O.Length(2147483647,varying=true))
    /** Database column content DBType(jsonb), Length(2147483647,false) */
    val content: Column[String] = column[String]("content", O.Length(2147483647,varying=false))
    /** Database column request DBType(varchar), Length(2147483647,true) */
    val request: Column[String] = column[String]("request", O.Length(2147483647,varying=true))
    /** Database column response DBType(varchar), Length(2147483647,true) */
    val response: Column[String] = column[String]("response", O.Length(2147483647,varying=true))
    /** Database column created_at DBType(timestamp) */
    val createdAt: Column[java.sql.Timestamp] = column[java.sql.Timestamp]("created_at")
  }
  /** Collection-like TableQuery object for table Logs */
  lazy val Logs = new TableQuery(tag => new Logs(tag))
  
  /** Entity class storing rows of table Portals
   *  @param id Database column id DBType(serial), AutoInc, PrimaryKey
   *  @param guid Database column guid DBType(varchar), Length(2147483647,true)
   *  @param tileKey Database column tile_key DBType(varchar), Length(2147483647,true)
   *  @param late6 Database column late6 DBType(int4)
   *  @param lnge6 Database column lnge6 DBType(int4)
   *  @param title Database column title DBType(varchar), Length(2147483647,true)
   *  @param image Database column image DBType(varchar), Length(2147483647,true)
   *  @param nearlyGeohash Database column nearly_geohash DBType(bpchar), Length(5,false), Default(None)
   *  @param createdAt Database column created_at DBType(timestamp)
   *  @param updatedAt Database column updated_at DBType(timestamp)
   *  @param deletedAt Database column deleted_at DBType(timestamp), Default(None) */
  case class PortalsRow(id: Int, guid: String, tileKey: String, late6: Int, lnge6: Int, title: String, image: String, nearlyGeohash: Option[String] = None, createdAt: java.sql.Timestamp, updatedAt: java.sql.Timestamp, deletedAt: Option[java.sql.Timestamp] = None)
  /** GetResult implicit for fetching PortalsRow objects using plain SQL queries */
  implicit def GetResultPortalsRow(implicit e0: GR[Int], e1: GR[String], e2: GR[Option[String]], e3: GR[java.sql.Timestamp], e4: GR[Option[java.sql.Timestamp]]): GR[PortalsRow] = GR{
    prs => import prs._
    PortalsRow.tupled((<<[Int], <<[String], <<[String], <<[Int], <<[Int], <<[String], <<[String], <<?[String], <<[java.sql.Timestamp], <<[java.sql.Timestamp], <<?[java.sql.Timestamp]))
  }
  /** Table description of table portals. Objects of this class serve as prototypes for rows in queries. */
  class Portals(_tableTag: Tag) extends Table[PortalsRow](_tableTag, Some("intel"), "portals") {
    def * = (id, guid, tileKey, late6, lnge6, title, image, nearlyGeohash, createdAt, updatedAt, deletedAt) <> (PortalsRow.tupled, PortalsRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (id.?, guid.?, tileKey.?, late6.?, lnge6.?, title.?, image.?, nearlyGeohash, createdAt.?, updatedAt.?, deletedAt).shaped.<>({r=>import r._; _1.map(_=> PortalsRow.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6.get, _7.get, _8, _9.get, _10.get, _11)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))
    
    /** Database column id DBType(serial), AutoInc, PrimaryKey */
    val id: Column[Int] = column[Int]("id", O.AutoInc, O.PrimaryKey)
    /** Database column guid DBType(varchar), Length(2147483647,true) */
    val guid: Column[String] = column[String]("guid", O.Length(2147483647,varying=true))
    /** Database column tile_key DBType(varchar), Length(2147483647,true) */
    val tileKey: Column[String] = column[String]("tile_key", O.Length(2147483647,varying=true))
    /** Database column late6 DBType(int4) */
    val late6: Column[Int] = column[Int]("late6")
    /** Database column lnge6 DBType(int4) */
    val lnge6: Column[Int] = column[Int]("lnge6")
    /** Database column title DBType(varchar), Length(2147483647,true) */
    val title: Column[String] = column[String]("title", O.Length(2147483647,varying=true))
    /** Database column image DBType(varchar), Length(2147483647,true) */
    val image: Column[String] = column[String]("image", O.Length(2147483647,varying=true))
    /** Database column nearly_geohash DBType(bpchar), Length(5,false), Default(None) */
    val nearlyGeohash: Column[Option[String]] = column[Option[String]]("nearly_geohash", O.Length(5,varying=false), O.Default(None))
    /** Database column created_at DBType(timestamp) */
    val createdAt: Column[java.sql.Timestamp] = column[java.sql.Timestamp]("created_at")
    /** Database column updated_at DBType(timestamp) */
    val updatedAt: Column[java.sql.Timestamp] = column[java.sql.Timestamp]("updated_at")
    /** Database column deleted_at DBType(timestamp), Default(None) */
    val deletedAt: Column[Option[java.sql.Timestamp]] = column[Option[java.sql.Timestamp]]("deleted_at", O.Default(None))
    
    /** Uniqueness Index over (guid) (database name portal_idx00) */
    val index1 = index("portal_idx00", guid, unique=true)
    /** Uniqueness Index over (guid) (database name portals_guid_key) */
    val index2 = index("portals_guid_key", guid, unique=true)
  }
  /** Collection-like TableQuery object for table Portals */
  lazy val Portals = new TableQuery(tag => new Portals(tag))
  
  /** Entity class storing rows of table PortalStateLogs
   *  @param id Database column id DBType(serial), AutoInc, PrimaryKey
   *  @param portalId Database column portal_id DBType(int4)
   *  @param owner Database column owner DBType(varchar), Length(2147483647,true)
   *  @param level Database column level DBType(int2)
   *  @param health Database column health DBType(int2)
   *  @param team Database column team DBType(bpchar), Length(1,false)
   *  @param mitigation Database column mitigation DBType(int2)
   *  @param resCount Database column res_count DBType(int2)
   *  @param resonators Database column resonators DBType(jsonb), Length(2147483647,false)
   *  @param mods Database column mods DBType(jsonb), Length(2147483647,false)
   *  @param createdAt Database column created_at DBType(timestamp) */
  case class PortalStateLogsRow(id: Int, portalId: Int, owner: String, level: Short, health: Short, team: String, mitigation: Short, resCount: Short, resonators: String, mods: String, createdAt: java.sql.Timestamp)
  /** GetResult implicit for fetching PortalStateLogsRow objects using plain SQL queries */
  implicit def GetResultPortalStateLogsRow(implicit e0: GR[Int], e1: GR[String], e2: GR[Short], e3: GR[java.sql.Timestamp]): GR[PortalStateLogsRow] = GR{
    prs => import prs._
    PortalStateLogsRow.tupled((<<[Int], <<[Int], <<[String], <<[Short], <<[Short], <<[String], <<[Short], <<[Short], <<[String], <<[String], <<[java.sql.Timestamp]))
  }
  /** Table description of table portal_state_logs. Objects of this class serve as prototypes for rows in queries. */
  class PortalStateLogs(_tableTag: Tag) extends Table[PortalStateLogsRow](_tableTag, Some("intel"), "portal_state_logs") {
    def * = (id, portalId, owner, level, health, team, mitigation, resCount, resonators, mods, createdAt) <> (PortalStateLogsRow.tupled, PortalStateLogsRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (id.?, portalId.?, owner.?, level.?, health.?, team.?, mitigation.?, resCount.?, resonators.?, mods.?, createdAt.?).shaped.<>({r=>import r._; _1.map(_=> PortalStateLogsRow.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6.get, _7.get, _8.get, _9.get, _10.get, _11.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))
    
    /** Database column id DBType(serial), AutoInc, PrimaryKey */
    val id: Column[Int] = column[Int]("id", O.AutoInc, O.PrimaryKey)
    /** Database column portal_id DBType(int4) */
    val portalId: Column[Int] = column[Int]("portal_id")
    /** Database column owner DBType(varchar), Length(2147483647,true) */
    val owner: Column[String] = column[String]("owner", O.Length(2147483647,varying=true))
    /** Database column level DBType(int2) */
    val level: Column[Short] = column[Short]("level")
    /** Database column health DBType(int2) */
    val health: Column[Short] = column[Short]("health")
    /** Database column team DBType(bpchar), Length(1,false) */
    val team: Column[String] = column[String]("team", O.Length(1,varying=false))
    /** Database column mitigation DBType(int2) */
    val mitigation: Column[Short] = column[Short]("mitigation")
    /** Database column res_count DBType(int2) */
    val resCount: Column[Short] = column[Short]("res_count")
    /** Database column resonators DBType(jsonb), Length(2147483647,false) */
    val resonators: Column[String] = column[String]("resonators", O.Length(2147483647,varying=false))
    /** Database column mods DBType(jsonb), Length(2147483647,false) */
    val mods: Column[String] = column[String]("mods", O.Length(2147483647,varying=false))
    /** Database column created_at DBType(timestamp) */
    val createdAt: Column[java.sql.Timestamp] = column[java.sql.Timestamp]("created_at")
    
    /** Foreign key referencing Portals (database name portal_state_logs_portal_id_fkey) */
    lazy val portalsFk = foreignKey("portal_state_logs_portal_id_fkey", portalId, Portals)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.Cascade)
  }
  /** Collection-like TableQuery object for table PortalStateLogs */
  lazy val PortalStateLogs = new TableQuery(tag => new PortalStateLogs(tag))
}