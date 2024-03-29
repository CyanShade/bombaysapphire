package org.koiroha.bombaysapphire.garuda.schema
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
  lazy val ddl = _Temp.ddl ++ Agents.ddl ++ FarmActivities.ddl ++ FarmPortals.ddl ++ FarmRegions.ddl ++ Farms.ddl ++ Geohash.ddl ++ HeuristicRegions.ddl ++ Logs.ddl ++ Plexts.ddl ++ PortalEventLogs.ddl ++ Portals.ddl ++ PortalStateLogs.ddl ++ SentinelTasks.ddl
  
  /** Entity class storing rows of table _Temp
   *  @param r Database column r DBType(polygon), Length(2147483647,false) */
  case class _TempRow(r: String)
  /** GetResult implicit for fetching _TempRow objects using plain SQL queries */
  implicit def GetResult_TempRow(implicit e0: GR[String]): GR[_TempRow] = GR{
    prs => import prs._
    _TempRow(<<[String])
  }
  /** Table description of table _temp. Objects of this class serve as prototypes for rows in queries. */
  class _Temp(_tableTag: Tag) extends Table[_TempRow](_tableTag, "_temp") {
    def * = r <> (_TempRow, _TempRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = r.?.shaped.<>(r => r.map(_=> _TempRow(r.get)), (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))
    
    /** Database column r DBType(polygon), Length(2147483647,false) */
    val r: Column[String] = column[String]("r", O.Length(2147483647,varying=false))
  }
  /** Collection-like TableQuery object for table _Temp */
  lazy val _Temp = new TableQuery(tag => new _Temp(tag))
  
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
  
  /** Entity class storing rows of table FarmActivities
   *  @param id Database column id DBType(serial), AutoInc, PrimaryKey
   *  @param farmId Database column farm_id DBType(int4)
   *  @param strictPortalCount Database column strict_portal_count DBType(int4), Default(0)
   *  @param portalCount Database column portal_count DBType(int4), Default(0)
   *  @param portalCountR Database column portal_count_r DBType(int4), Default(0)
   *  @param portalCountE Database column portal_count_e DBType(int4), Default(0)
   *  @param p8ReachR Database column p8_reach_r DBType(int4), Default(0)
   *  @param p8ReachE Database column p8_reach_e DBType(int4), Default(0)
   *  @param avrResLevelR Database column avr_res_level_r DBType(float4), Default(0.0)
   *  @param avrResLevelE Database column avr_res_level_e DBType(float4), Default(0.0)
   *  @param avrResonatorR Database column avr_resonator_r DBType(float4), Default(0.0)
   *  @param avrResonatorE Database column avr_resonator_e DBType(float4), Default(0.0)
   *  @param avrModR Database column avr_mod_r DBType(float4), Default(0.0)
   *  @param avrModE Database column avr_mod_e DBType(float4), Default(0.0)
   *  @param avrMitigationR Database column avr_mitigation_r DBType(float4), Default(0.0)
   *  @param avrMitigationE Database column avr_mitigation_e DBType(float4), Default(0.0)
   *  @param avrCooldownRatio Database column avr_cooldown_ratio DBType(float4), Default(0.0)
   *  @param additionalHack Database column additional_hack DBType(int4), Default(0)
   *  @param measuredAt Database column measured_at DBType(timestamp)
   *  @param createdAt Database column created_at DBType(timestamp) */
  case class FarmActivitiesRow(id: Int, farmId: Int, strictPortalCount: Int = 0, portalCount: Int = 0, portalCountR: Int = 0, portalCountE: Int = 0, p8ReachR: Int = 0, p8ReachE: Int = 0, avrResLevelR: Float = 0.0F, avrResLevelE: Float = 0.0F, avrResonatorR: Float = 0.0F, avrResonatorE: Float = 0.0F, avrModR: Float = 0.0F, avrModE: Float = 0.0F, avrMitigationR: Float = 0.0F, avrMitigationE: Float = 0.0F, avrCooldownRatio: Float = 0.0F, additionalHack: Int = 0, measuredAt: java.sql.Timestamp, createdAt: java.sql.Timestamp)
  /** GetResult implicit for fetching FarmActivitiesRow objects using plain SQL queries */
  implicit def GetResultFarmActivitiesRow(implicit e0: GR[Int], e1: GR[Float], e2: GR[java.sql.Timestamp]): GR[FarmActivitiesRow] = GR{
    prs => import prs._
    FarmActivitiesRow.tupled((<<[Int], <<[Int], <<[Int], <<[Int], <<[Int], <<[Int], <<[Int], <<[Int], <<[Float], <<[Float], <<[Float], <<[Float], <<[Float], <<[Float], <<[Float], <<[Float], <<[Float], <<[Int], <<[java.sql.Timestamp], <<[java.sql.Timestamp]))
  }
  /** Table description of table farm_activities. Objects of this class serve as prototypes for rows in queries. */
  class FarmActivities(_tableTag: Tag) extends Table[FarmActivitiesRow](_tableTag, Some("intel"), "farm_activities") {
    def * = (id, farmId, strictPortalCount, portalCount, portalCountR, portalCountE, p8ReachR, p8ReachE, avrResLevelR, avrResLevelE, avrResonatorR, avrResonatorE, avrModR, avrModE, avrMitigationR, avrMitigationE, avrCooldownRatio, additionalHack, measuredAt, createdAt) <> (FarmActivitiesRow.tupled, FarmActivitiesRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (id.?, farmId.?, strictPortalCount.?, portalCount.?, portalCountR.?, portalCountE.?, p8ReachR.?, p8ReachE.?, avrResLevelR.?, avrResLevelE.?, avrResonatorR.?, avrResonatorE.?, avrModR.?, avrModE.?, avrMitigationR.?, avrMitigationE.?, avrCooldownRatio.?, additionalHack.?, measuredAt.?, createdAt.?).shaped.<>({r=>import r._; _1.map(_=> FarmActivitiesRow.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6.get, _7.get, _8.get, _9.get, _10.get, _11.get, _12.get, _13.get, _14.get, _15.get, _16.get, _17.get, _18.get, _19.get, _20.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))
    
    /** Database column id DBType(serial), AutoInc, PrimaryKey */
    val id: Column[Int] = column[Int]("id", O.AutoInc, O.PrimaryKey)
    /** Database column farm_id DBType(int4) */
    val farmId: Column[Int] = column[Int]("farm_id")
    /** Database column strict_portal_count DBType(int4), Default(0) */
    val strictPortalCount: Column[Int] = column[Int]("strict_portal_count", O.Default(0))
    /** Database column portal_count DBType(int4), Default(0) */
    val portalCount: Column[Int] = column[Int]("portal_count", O.Default(0))
    /** Database column portal_count_r DBType(int4), Default(0) */
    val portalCountR: Column[Int] = column[Int]("portal_count_r", O.Default(0))
    /** Database column portal_count_e DBType(int4), Default(0) */
    val portalCountE: Column[Int] = column[Int]("portal_count_e", O.Default(0))
    /** Database column p8_reach_r DBType(int4), Default(0) */
    val p8ReachR: Column[Int] = column[Int]("p8_reach_r", O.Default(0))
    /** Database column p8_reach_e DBType(int4), Default(0) */
    val p8ReachE: Column[Int] = column[Int]("p8_reach_e", O.Default(0))
    /** Database column avr_res_level_r DBType(float4), Default(0.0) */
    val avrResLevelR: Column[Float] = column[Float]("avr_res_level_r", O.Default(0.0F))
    /** Database column avr_res_level_e DBType(float4), Default(0.0) */
    val avrResLevelE: Column[Float] = column[Float]("avr_res_level_e", O.Default(0.0F))
    /** Database column avr_resonator_r DBType(float4), Default(0.0) */
    val avrResonatorR: Column[Float] = column[Float]("avr_resonator_r", O.Default(0.0F))
    /** Database column avr_resonator_e DBType(float4), Default(0.0) */
    val avrResonatorE: Column[Float] = column[Float]("avr_resonator_e", O.Default(0.0F))
    /** Database column avr_mod_r DBType(float4), Default(0.0) */
    val avrModR: Column[Float] = column[Float]("avr_mod_r", O.Default(0.0F))
    /** Database column avr_mod_e DBType(float4), Default(0.0) */
    val avrModE: Column[Float] = column[Float]("avr_mod_e", O.Default(0.0F))
    /** Database column avr_mitigation_r DBType(float4), Default(0.0) */
    val avrMitigationR: Column[Float] = column[Float]("avr_mitigation_r", O.Default(0.0F))
    /** Database column avr_mitigation_e DBType(float4), Default(0.0) */
    val avrMitigationE: Column[Float] = column[Float]("avr_mitigation_e", O.Default(0.0F))
    /** Database column avr_cooldown_ratio DBType(float4), Default(0.0) */
    val avrCooldownRatio: Column[Float] = column[Float]("avr_cooldown_ratio", O.Default(0.0F))
    /** Database column additional_hack DBType(int4), Default(0) */
    val additionalHack: Column[Int] = column[Int]("additional_hack", O.Default(0))
    /** Database column measured_at DBType(timestamp) */
    val measuredAt: Column[java.sql.Timestamp] = column[java.sql.Timestamp]("measured_at")
    /** Database column created_at DBType(timestamp) */
    val createdAt: Column[java.sql.Timestamp] = column[java.sql.Timestamp]("created_at")
    
    /** Foreign key referencing Farms (database name farm_activities_farm_id_fkey) */
    lazy val farmsFk = foreignKey("farm_activities_farm_id_fkey", farmId, Farms)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.Cascade)
    
    /** Index over (createdAt) (database name farm_activities_idx01) */
    val index1 = index("farm_activities_idx01", createdAt)
  }
  /** Collection-like TableQuery object for table FarmActivities */
  lazy val FarmActivities = new TableQuery(tag => new FarmActivities(tag))
  
  /** Entity class storing rows of table FarmPortals
   *  @param id Database column id DBType(serial), AutoInc, PrimaryKey
   *  @param farmId Database column farm_id DBType(int4)
   *  @param portalId Database column portal_id DBType(int4) */
  case class FarmPortalsRow(id: Int, farmId: Int, portalId: Int)
  /** GetResult implicit for fetching FarmPortalsRow objects using plain SQL queries */
  implicit def GetResultFarmPortalsRow(implicit e0: GR[Int]): GR[FarmPortalsRow] = GR{
    prs => import prs._
    FarmPortalsRow.tupled((<<[Int], <<[Int], <<[Int]))
  }
  /** Table description of table farm_portals. Objects of this class serve as prototypes for rows in queries. */
  class FarmPortals(_tableTag: Tag) extends Table[FarmPortalsRow](_tableTag, Some("intel"), "farm_portals") {
    def * = (id, farmId, portalId) <> (FarmPortalsRow.tupled, FarmPortalsRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (id.?, farmId.?, portalId.?).shaped.<>({r=>import r._; _1.map(_=> FarmPortalsRow.tupled((_1.get, _2.get, _3.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))
    
    /** Database column id DBType(serial), AutoInc, PrimaryKey */
    val id: Column[Int] = column[Int]("id", O.AutoInc, O.PrimaryKey)
    /** Database column farm_id DBType(int4) */
    val farmId: Column[Int] = column[Int]("farm_id")
    /** Database column portal_id DBType(int4) */
    val portalId: Column[Int] = column[Int]("portal_id")
    
    /** Foreign key referencing Farms (database name farm_portals_farm_id_fkey) */
    lazy val farmsFk = foreignKey("farm_portals_farm_id_fkey", farmId, Farms)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.Cascade)
    /** Foreign key referencing Portals (database name farm_portals_portal_id_fkey) */
    lazy val portalsFk = foreignKey("farm_portals_portal_id_fkey", portalId, Portals)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.Cascade)
  }
  /** Collection-like TableQuery object for table FarmPortals */
  lazy val FarmPortals = new TableQuery(tag => new FarmPortals(tag))
  
  /** Entity class storing rows of table FarmRegions
   *  @param id Database column id DBType(serial), AutoInc, PrimaryKey
   *  @param farmId Database column farm_id DBType(int4)
   *  @param region Database column region DBType(polygon), Length(2147483647,false)
   *  @param createdAt Database column created_at DBType(timestamp) */
  case class FarmRegionsRow(id: Int, farmId: Int, region: String, createdAt: java.sql.Timestamp)
  /** GetResult implicit for fetching FarmRegionsRow objects using plain SQL queries */
  implicit def GetResultFarmRegionsRow(implicit e0: GR[Int], e1: GR[String], e2: GR[java.sql.Timestamp]): GR[FarmRegionsRow] = GR{
    prs => import prs._
    FarmRegionsRow.tupled((<<[Int], <<[Int], <<[String], <<[java.sql.Timestamp]))
  }
  /** Table description of table farm_regions. Objects of this class serve as prototypes for rows in queries. */
  class FarmRegions(_tableTag: Tag) extends Table[FarmRegionsRow](_tableTag, Some("intel"), "farm_regions") {
    def * = (id, farmId, region, createdAt) <> (FarmRegionsRow.tupled, FarmRegionsRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (id.?, farmId.?, region.?, createdAt.?).shaped.<>({r=>import r._; _1.map(_=> FarmRegionsRow.tupled((_1.get, _2.get, _3.get, _4.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))
    
    /** Database column id DBType(serial), AutoInc, PrimaryKey */
    val id: Column[Int] = column[Int]("id", O.AutoInc, O.PrimaryKey)
    /** Database column farm_id DBType(int4) */
    val farmId: Column[Int] = column[Int]("farm_id")
    /** Database column region DBType(polygon), Length(2147483647,false) */
    val region: Column[String] = column[String]("region", O.Length(2147483647,varying=false))
    /** Database column created_at DBType(timestamp) */
    val createdAt: Column[java.sql.Timestamp] = column[java.sql.Timestamp]("created_at")
    
    /** Foreign key referencing Farms (database name farm_regions_farm_id_fkey) */
    lazy val farmsFk = foreignKey("farm_regions_farm_id_fkey", farmId, Farms)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.Cascade)
  }
  /** Collection-like TableQuery object for table FarmRegions */
  lazy val FarmRegions = new TableQuery(tag => new FarmRegions(tag))
  
  /** Entity class storing rows of table Farms
   *  @param id Database column id DBType(serial), AutoInc, PrimaryKey
   *  @param parent Database column parent DBType(int4), Default(None)
   *  @param name Database column name DBType(varchar), Length(2147483647,true)
   *  @param address Database column address DBType(varchar), Length(2147483647,true), Default()
   *  @param description Database column description DBType(text), Length(2147483647,true)
   *  @param formattedDescription Database column formatted_description DBType(text), Length(2147483647,true)
   *  @param icon Database column icon DBType(bytea), Default(None)
   *  @param externalKmlUrl Database column external_kml_url DBType(text), Length(2147483647,true), Default(None)
   *  @param latestActivity Database column latest_activity DBType(int4), Default(None)
   *  @param createdAt Database column created_at DBType(timestamp)
   *  @param updatedAt Database column updated_at DBType(timestamp) */
  case class FarmsRow(id: Int, parent: Option[Int] = None, name: String, address: String = "", description: String, formattedDescription: String, icon: Option[java.sql.Blob] = None, externalKmlUrl: Option[String] = None, latestActivity: Option[Int] = None, createdAt: java.sql.Timestamp, updatedAt: java.sql.Timestamp)
  /** GetResult implicit for fetching FarmsRow objects using plain SQL queries */
  implicit def GetResultFarmsRow(implicit e0: GR[Int], e1: GR[Option[Int]], e2: GR[String], e3: GR[Option[java.sql.Blob]], e4: GR[Option[String]], e5: GR[java.sql.Timestamp]): GR[FarmsRow] = GR{
    prs => import prs._
    FarmsRow.tupled((<<[Int], <<?[Int], <<[String], <<[String], <<[String], <<[String], <<?[java.sql.Blob], <<?[String], <<?[Int], <<[java.sql.Timestamp], <<[java.sql.Timestamp]))
  }
  /** Table description of table farms. Objects of this class serve as prototypes for rows in queries. */
  class Farms(_tableTag: Tag) extends Table[FarmsRow](_tableTag, Some("intel"), "farms") {
    def * = (id, parent, name, address, description, formattedDescription, icon, externalKmlUrl, latestActivity, createdAt, updatedAt) <> (FarmsRow.tupled, FarmsRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (id.?, parent, name.?, address.?, description.?, formattedDescription.?, icon, externalKmlUrl, latestActivity, createdAt.?, updatedAt.?).shaped.<>({r=>import r._; _1.map(_=> FarmsRow.tupled((_1.get, _2, _3.get, _4.get, _5.get, _6.get, _7, _8, _9, _10.get, _11.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))
    
    /** Database column id DBType(serial), AutoInc, PrimaryKey */
    val id: Column[Int] = column[Int]("id", O.AutoInc, O.PrimaryKey)
    /** Database column parent DBType(int4), Default(None) */
    val parent: Column[Option[Int]] = column[Option[Int]]("parent", O.Default(None))
    /** Database column name DBType(varchar), Length(2147483647,true) */
    val name: Column[String] = column[String]("name", O.Length(2147483647,varying=true))
    /** Database column address DBType(varchar), Length(2147483647,true), Default() */
    val address: Column[String] = column[String]("address", O.Length(2147483647,varying=true), O.Default(""))
    /** Database column description DBType(text), Length(2147483647,true) */
    val description: Column[String] = column[String]("description", O.Length(2147483647,varying=true))
    /** Database column formatted_description DBType(text), Length(2147483647,true) */
    val formattedDescription: Column[String] = column[String]("formatted_description", O.Length(2147483647,varying=true))
    /** Database column icon DBType(bytea), Default(None) */
    val icon: Column[Option[java.sql.Blob]] = column[Option[java.sql.Blob]]("icon", O.Default(None))
    /** Database column external_kml_url DBType(text), Length(2147483647,true), Default(None) */
    val externalKmlUrl: Column[Option[String]] = column[Option[String]]("external_kml_url", O.Length(2147483647,varying=true), O.Default(None))
    /** Database column latest_activity DBType(int4), Default(None) */
    val latestActivity: Column[Option[Int]] = column[Option[Int]]("latest_activity", O.Default(None))
    /** Database column created_at DBType(timestamp) */
    val createdAt: Column[java.sql.Timestamp] = column[java.sql.Timestamp]("created_at")
    /** Database column updated_at DBType(timestamp) */
    val updatedAt: Column[java.sql.Timestamp] = column[java.sql.Timestamp]("updated_at")
    
    /** Index over (parent) (database name farms_idx01) */
    val index1 = index("farms_idx01", parent)
    /** Index over (name) (database name farms_idx02) */
    val index2 = index("farms_idx02", name)
  }
  /** Collection-like TableQuery object for table Farms */
  lazy val Farms = new TableQuery(tag => new Farms(tag))
  
  /** Entity class storing rows of table Geohash
   *  @param geohash Database column geohash DBType(bpchar), PrimaryKey, Length(10,false)
   *  @param late6 Database column late6 DBType(int4)
   *  @param lnge6 Database column lnge6 DBType(int4)
   *  @param country Database column country DBType(bpchar), Length(2,false)
   *  @param state Database column state DBType(varchar), Length(2147483647,true)
   *  @param city Database column city DBType(varchar), Length(2147483647,true)
   *  @param createdAt Database column created_at DBType(timestamp) */
  case class GeohashRow(geohash: String, late6: Int, lnge6: Int, country: String, state: String, city: String, createdAt: java.sql.Timestamp)
  /** GetResult implicit for fetching GeohashRow objects using plain SQL queries */
  implicit def GetResultGeohashRow(implicit e0: GR[String], e1: GR[Int], e2: GR[java.sql.Timestamp]): GR[GeohashRow] = GR{
    prs => import prs._
    GeohashRow.tupled((<<[String], <<[Int], <<[Int], <<[String], <<[String], <<[String], <<[java.sql.Timestamp]))
  }
  /** Table description of table geohash. Objects of this class serve as prototypes for rows in queries. */
  class Geohash(_tableTag: Tag) extends Table[GeohashRow](_tableTag, Some("intel"), "geohash") {
    def * = (geohash, late6, lnge6, country, state, city, createdAt) <> (GeohashRow.tupled, GeohashRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (geohash.?, late6.?, lnge6.?, country.?, state.?, city.?, createdAt.?).shaped.<>({r=>import r._; _1.map(_=> GeohashRow.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6.get, _7.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))
    
    /** Database column geohash DBType(bpchar), PrimaryKey, Length(10,false) */
    val geohash: Column[String] = column[String]("geohash", O.PrimaryKey, O.Length(10,varying=false))
    /** Database column late6 DBType(int4) */
    val late6: Column[Int] = column[Int]("late6")
    /** Database column lnge6 DBType(int4) */
    val lnge6: Column[Int] = column[Int]("lnge6")
    /** Database column country DBType(bpchar), Length(2,false) */
    val country: Column[String] = column[String]("country", O.Length(2,varying=false))
    /** Database column state DBType(varchar), Length(2147483647,true) */
    val state: Column[String] = column[String]("state", O.Length(2147483647,varying=true))
    /** Database column city DBType(varchar), Length(2147483647,true) */
    val city: Column[String] = column[String]("city", O.Length(2147483647,varying=true))
    /** Database column created_at DBType(timestamp) */
    val createdAt: Column[java.sql.Timestamp] = column[java.sql.Timestamp]("created_at")
  }
  /** Collection-like TableQuery object for table Geohash */
  lazy val Geohash = new TableQuery(tag => new Geohash(tag))
  
  /** Entity class storing rows of table HeuristicRegions
   *  @param id Database column id DBType(serial), AutoInc, PrimaryKey
   *  @param country Database column country DBType(bpchar), Length(2,false)
   *  @param state Database column state DBType(varchar), Length(2147483647,true), Default(None)
   *  @param city Database column city DBType(varchar), Length(2147483647,true), Default(None)
   *  @param side Database column side DBType(bpchar), Length(1,false)
   *  @param seq Database column seq DBType(int4)
   *  @param region Database column region DBType(polygon), Length(2147483647,false)
   *  @param createdAt Database column created_at DBType(timestamp)
   *  @param updatedAt Database column updated_at DBType(timestamp) */
  case class HeuristicRegionsRow(id: Int, country: String, state: Option[String] = None, city: Option[String] = None, side: String, seq: Int, region: String, createdAt: java.sql.Timestamp, updatedAt: java.sql.Timestamp)
  /** GetResult implicit for fetching HeuristicRegionsRow objects using plain SQL queries */
  implicit def GetResultHeuristicRegionsRow(implicit e0: GR[Int], e1: GR[String], e2: GR[Option[String]], e3: GR[java.sql.Timestamp]): GR[HeuristicRegionsRow] = GR{
    prs => import prs._
    HeuristicRegionsRow.tupled((<<[Int], <<[String], <<?[String], <<?[String], <<[String], <<[Int], <<[String], <<[java.sql.Timestamp], <<[java.sql.Timestamp]))
  }
  /** Table description of table heuristic_regions. Objects of this class serve as prototypes for rows in queries. */
  class HeuristicRegions(_tableTag: Tag) extends Table[HeuristicRegionsRow](_tableTag, Some("intel"), "heuristic_regions") {
    def * = (id, country, state, city, side, seq, region, createdAt, updatedAt) <> (HeuristicRegionsRow.tupled, HeuristicRegionsRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (id.?, country.?, state, city, side.?, seq.?, region.?, createdAt.?, updatedAt.?).shaped.<>({r=>import r._; _1.map(_=> HeuristicRegionsRow.tupled((_1.get, _2.get, _3, _4, _5.get, _6.get, _7.get, _8.get, _9.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))
    
    /** Database column id DBType(serial), AutoInc, PrimaryKey */
    val id: Column[Int] = column[Int]("id", O.AutoInc, O.PrimaryKey)
    /** Database column country DBType(bpchar), Length(2,false) */
    val country: Column[String] = column[String]("country", O.Length(2,varying=false))
    /** Database column state DBType(varchar), Length(2147483647,true), Default(None) */
    val state: Column[Option[String]] = column[Option[String]]("state", O.Length(2147483647,varying=true), O.Default(None))
    /** Database column city DBType(varchar), Length(2147483647,true), Default(None) */
    val city: Column[Option[String]] = column[Option[String]]("city", O.Length(2147483647,varying=true), O.Default(None))
    /** Database column side DBType(bpchar), Length(1,false) */
    val side: Column[String] = column[String]("side", O.Length(1,varying=false))
    /** Database column seq DBType(int4) */
    val seq: Column[Int] = column[Int]("seq")
    /** Database column region DBType(polygon), Length(2147483647,false) */
    val region: Column[String] = column[String]("region", O.Length(2147483647,varying=false))
    /** Database column created_at DBType(timestamp) */
    val createdAt: Column[java.sql.Timestamp] = column[java.sql.Timestamp]("created_at")
    /** Database column updated_at DBType(timestamp) */
    val updatedAt: Column[java.sql.Timestamp] = column[java.sql.Timestamp]("updated_at")
    
    /** Uniqueness Index over (country,state,city,seq,side) (database name heuristic_regions_country_state_city_seq_side_key) */
    val index1 = index("heuristic_regions_country_state_city_seq_side_key", (country, state, city, seq, side), unique=true)
  }
  /** Collection-like TableQuery object for table HeuristicRegions */
  lazy val HeuristicRegions = new TableQuery(tag => new HeuristicRegions(tag))
  
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
  
  /** Entity class storing rows of table Plexts
   *  @param id Database column id DBType(bigserial), AutoInc, PrimaryKey
   *  @param guid Database column guid DBType(varchar), Length(2147483647,true)
   *  @param unknown Database column unknown DBType(float8)
   *  @param category Database column category DBType(int4)
   *  @param markup Database column markup DBType(jsonb), Length(2147483647,false)
   *  @param plextType Database column plext_type DBType(varchar), Length(2147483647,true)
   *  @param team Database column team DBType(bpchar), Length(1,false)
   *  @param text Database column text DBType(varchar), Length(2147483647,true)
   *  @param createdAt Database column created_at DBType(timestamp) */
  case class PlextsRow(id: Long, guid: String, unknown: Double, category: Int, markup: String, plextType: String, team: String, text: String, createdAt: java.sql.Timestamp)
  /** GetResult implicit for fetching PlextsRow objects using plain SQL queries */
  implicit def GetResultPlextsRow(implicit e0: GR[Long], e1: GR[String], e2: GR[Double], e3: GR[Int], e4: GR[java.sql.Timestamp]): GR[PlextsRow] = GR{
    prs => import prs._
    PlextsRow.tupled((<<[Long], <<[String], <<[Double], <<[Int], <<[String], <<[String], <<[String], <<[String], <<[java.sql.Timestamp]))
  }
  /** Table description of table plexts. Objects of this class serve as prototypes for rows in queries. */
  class Plexts(_tableTag: Tag) extends Table[PlextsRow](_tableTag, Some("intel"), "plexts") {
    def * = (id, guid, unknown, category, markup, plextType, team, text, createdAt) <> (PlextsRow.tupled, PlextsRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (id.?, guid.?, unknown.?, category.?, markup.?, plextType.?, team.?, text.?, createdAt.?).shaped.<>({r=>import r._; _1.map(_=> PlextsRow.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6.get, _7.get, _8.get, _9.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))
    
    /** Database column id DBType(bigserial), AutoInc, PrimaryKey */
    val id: Column[Long] = column[Long]("id", O.AutoInc, O.PrimaryKey)
    /** Database column guid DBType(varchar), Length(2147483647,true) */
    val guid: Column[String] = column[String]("guid", O.Length(2147483647,varying=true))
    /** Database column unknown DBType(float8) */
    val unknown: Column[Double] = column[Double]("unknown")
    /** Database column category DBType(int4) */
    val category: Column[Int] = column[Int]("category")
    /** Database column markup DBType(jsonb), Length(2147483647,false) */
    val markup: Column[String] = column[String]("markup", O.Length(2147483647,varying=false))
    /** Database column plext_type DBType(varchar), Length(2147483647,true) */
    val plextType: Column[String] = column[String]("plext_type", O.Length(2147483647,varying=true))
    /** Database column team DBType(bpchar), Length(1,false) */
    val team: Column[String] = column[String]("team", O.Length(1,varying=false))
    /** Database column text DBType(varchar), Length(2147483647,true) */
    val text: Column[String] = column[String]("text", O.Length(2147483647,varying=true))
    /** Database column created_at DBType(timestamp) */
    val createdAt: Column[java.sql.Timestamp] = column[java.sql.Timestamp]("created_at")
    
    /** Uniqueness Index over (guid) (database name plexts_guid) */
    val index1 = index("plexts_guid", guid, unique=true)
    /** Uniqueness Index over (guid) (database name plexts_guid_key) */
    val index2 = index("plexts_guid_key", guid, unique=true)
  }
  /** Collection-like TableQuery object for table Plexts */
  lazy val Plexts = new TableQuery(tag => new Plexts(tag))
  
  /** Entity class storing rows of table PortalEventLogs
   *  @param id Database column id DBType(serial), AutoInc, PrimaryKey
   *  @param portalId Database column portal_id DBType(int4)
   *  @param action Database column action DBType(varchar), Length(2147483647,true)
   *  @param oldValue Database column old_value DBType(varchar), Length(2147483647,true), Default(None)
   *  @param newValue Database column new_value DBType(varchar), Length(2147483647,true), Default(None)
   *  @param createdAt Database column created_at DBType(timestamp)
   *  @param verifiedAt Database column verified_at DBType(timestamp), Default(None) */
  case class PortalEventLogsRow(id: Int, portalId: Int, action: String, oldValue: Option[String] = None, newValue: Option[String] = None, createdAt: java.sql.Timestamp, verifiedAt: Option[java.sql.Timestamp] = None)
  /** GetResult implicit for fetching PortalEventLogsRow objects using plain SQL queries */
  implicit def GetResultPortalEventLogsRow(implicit e0: GR[Int], e1: GR[String], e2: GR[Option[String]], e3: GR[java.sql.Timestamp], e4: GR[Option[java.sql.Timestamp]]): GR[PortalEventLogsRow] = GR{
    prs => import prs._
    PortalEventLogsRow.tupled((<<[Int], <<[Int], <<[String], <<?[String], <<?[String], <<[java.sql.Timestamp], <<?[java.sql.Timestamp]))
  }
  /** Table description of table portal_event_logs. Objects of this class serve as prototypes for rows in queries. */
  class PortalEventLogs(_tableTag: Tag) extends Table[PortalEventLogsRow](_tableTag, Some("intel"), "portal_event_logs") {
    def * = (id, portalId, action, oldValue, newValue, createdAt, verifiedAt) <> (PortalEventLogsRow.tupled, PortalEventLogsRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (id.?, portalId.?, action.?, oldValue, newValue, createdAt.?, verifiedAt).shaped.<>({r=>import r._; _1.map(_=> PortalEventLogsRow.tupled((_1.get, _2.get, _3.get, _4, _5, _6.get, _7)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))
    
    /** Database column id DBType(serial), AutoInc, PrimaryKey */
    val id: Column[Int] = column[Int]("id", O.AutoInc, O.PrimaryKey)
    /** Database column portal_id DBType(int4) */
    val portalId: Column[Int] = column[Int]("portal_id")
    /** Database column action DBType(varchar), Length(2147483647,true) */
    val action: Column[String] = column[String]("action", O.Length(2147483647,varying=true))
    /** Database column old_value DBType(varchar), Length(2147483647,true), Default(None) */
    val oldValue: Column[Option[String]] = column[Option[String]]("old_value", O.Length(2147483647,varying=true), O.Default(None))
    /** Database column new_value DBType(varchar), Length(2147483647,true), Default(None) */
    val newValue: Column[Option[String]] = column[Option[String]]("new_value", O.Length(2147483647,varying=true), O.Default(None))
    /** Database column created_at DBType(timestamp) */
    val createdAt: Column[java.sql.Timestamp] = column[java.sql.Timestamp]("created_at")
    /** Database column verified_at DBType(timestamp), Default(None) */
    val verifiedAt: Column[Option[java.sql.Timestamp]] = column[Option[java.sql.Timestamp]]("verified_at", O.Default(None))
    
    /** Foreign key referencing Portals (database name portal_event_logs_portal_id_fkey) */
    lazy val portalsFk = foreignKey("portal_event_logs_portal_id_fkey", portalId, Portals)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.Cascade)
  }
  /** Collection-like TableQuery object for table PortalEventLogs */
  lazy val PortalEventLogs = new TableQuery(tag => new PortalEventLogs(tag))
  
  /** Entity class storing rows of table Portals
   *  @param id Database column id DBType(serial), AutoInc, PrimaryKey
   *  @param guid Database column guid DBType(varchar), Length(2147483647,true)
   *  @param tileKey Database column tile_key DBType(varchar), Length(2147483647,true)
   *  @param late6 Database column late6 DBType(int4)
   *  @param lnge6 Database column lnge6 DBType(int4)
   *  @param title Database column title DBType(varchar), Length(2147483647,true)
   *  @param image Database column image DBType(varchar), Length(2147483647,true)
   *  @param createdAt Database column created_at DBType(timestamp)
   *  @param updatedAt Database column updated_at DBType(timestamp)
   *  @param deletedAt Database column deleted_at DBType(timestamp), Default(None)
   *  @param geohash Database column geohash DBType(bpchar), Length(10,false), Default(None)
   *  @param verifiedAt Database column verified_at DBType(timestamp)
   *  @param guardian Database column guardian DBType(int8), Default(0)
   *  @param mods Database column mods DBType(jsonb), Length(2147483647,false), Default(None)
   *  @param level Database column level DBType(int2), Default(0)
   *  @param team Database column team DBType(bpchar), Length(1,false) */
  case class PortalsRow(id: Int, guid: String, tileKey: String, late6: Int, lnge6: Int, title: String, image: String, createdAt: java.sql.Timestamp, updatedAt: java.sql.Timestamp, deletedAt: Option[java.sql.Timestamp] = None, geohash: Option[String] = None, verifiedAt: java.sql.Timestamp, guardian: Long = 0L, mods: Option[String] = None, level: Short = 0, team: String)
  /** GetResult implicit for fetching PortalsRow objects using plain SQL queries */
  implicit def GetResultPortalsRow(implicit e0: GR[Int], e1: GR[String], e2: GR[java.sql.Timestamp], e3: GR[Option[java.sql.Timestamp]], e4: GR[Option[String]], e5: GR[Long], e6: GR[Short]): GR[PortalsRow] = GR{
    prs => import prs._
    PortalsRow.tupled((<<[Int], <<[String], <<[String], <<[Int], <<[Int], <<[String], <<[String], <<[java.sql.Timestamp], <<[java.sql.Timestamp], <<?[java.sql.Timestamp], <<?[String], <<[java.sql.Timestamp], <<[Long], <<?[String], <<[Short], <<[String]))
  }
  /** Table description of table portals. Objects of this class serve as prototypes for rows in queries. */
  class Portals(_tableTag: Tag) extends Table[PortalsRow](_tableTag, Some("intel"), "portals") {
    def * = (id, guid, tileKey, late6, lnge6, title, image, createdAt, updatedAt, deletedAt, geohash, verifiedAt, guardian, mods, level, team) <> (PortalsRow.tupled, PortalsRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (id.?, guid.?, tileKey.?, late6.?, lnge6.?, title.?, image.?, createdAt.?, updatedAt.?, deletedAt, geohash, verifiedAt.?, guardian.?, mods, level.?, team.?).shaped.<>({r=>import r._; _1.map(_=> PortalsRow.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6.get, _7.get, _8.get, _9.get, _10, _11, _12.get, _13.get, _14, _15.get, _16.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))
    
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
    /** Database column created_at DBType(timestamp) */
    val createdAt: Column[java.sql.Timestamp] = column[java.sql.Timestamp]("created_at")
    /** Database column updated_at DBType(timestamp) */
    val updatedAt: Column[java.sql.Timestamp] = column[java.sql.Timestamp]("updated_at")
    /** Database column deleted_at DBType(timestamp), Default(None) */
    val deletedAt: Column[Option[java.sql.Timestamp]] = column[Option[java.sql.Timestamp]]("deleted_at", O.Default(None))
    /** Database column geohash DBType(bpchar), Length(10,false), Default(None) */
    val geohash: Column[Option[String]] = column[Option[String]]("geohash", O.Length(10,varying=false), O.Default(None))
    /** Database column verified_at DBType(timestamp) */
    val verifiedAt: Column[java.sql.Timestamp] = column[java.sql.Timestamp]("verified_at")
    /** Database column guardian DBType(int8), Default(0) */
    val guardian: Column[Long] = column[Long]("guardian", O.Default(0L))
    /** Database column mods DBType(jsonb), Length(2147483647,false), Default(None) */
    val mods: Column[Option[String]] = column[Option[String]]("mods", O.Length(2147483647,varying=false), O.Default(None))
    /** Database column level DBType(int2), Default(0) */
    val level: Column[Short] = column[Short]("level", O.Default(0))
    /** Database column team DBType(bpchar), Length(1,false) */
    val team: Column[String] = column[String]("team", O.Length(1,varying=false))
    
    /** Foreign key referencing Geohash (database name portals_geohash_fkey) */
    lazy val geohashFk = foreignKey("portals_geohash_fkey", geohash, Geohash)(r => r.geohash, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.SetNull)
    
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
   *  @param owner Database column owner DBType(varchar), Length(2147483647,true), Default(None)
   *  @param level Database column level DBType(int2)
   *  @param health Database column health DBType(int2)
   *  @param team Database column team DBType(bpchar), Length(1,false)
   *  @param mitigation Database column mitigation DBType(int2), Default(None)
   *  @param resCount Database column res_count DBType(int2)
   *  @param resonators Database column resonators DBType(jsonb), Length(2147483647,false), Default(None)
   *  @param mods Database column mods DBType(jsonb), Length(2147483647,false), Default(None)
   *  @param createdAt Database column created_at DBType(timestamp)
   *  @param artifact Database column artifact DBType(jsonb), Length(2147483647,false), Default(None) */
  case class PortalStateLogsRow(id: Int, portalId: Int, owner: Option[String] = None, level: Short, health: Short, team: String, mitigation: Option[Short] = None, resCount: Short, resonators: Option[String] = None, mods: Option[String] = None, createdAt: java.sql.Timestamp, artifact: Option[String] = None)
  /** GetResult implicit for fetching PortalStateLogsRow objects using plain SQL queries */
  implicit def GetResultPortalStateLogsRow(implicit e0: GR[Int], e1: GR[Option[String]], e2: GR[Short], e3: GR[String], e4: GR[Option[Short]], e5: GR[java.sql.Timestamp]): GR[PortalStateLogsRow] = GR{
    prs => import prs._
    PortalStateLogsRow.tupled((<<[Int], <<[Int], <<?[String], <<[Short], <<[Short], <<[String], <<?[Short], <<[Short], <<?[String], <<?[String], <<[java.sql.Timestamp], <<?[String]))
  }
  /** Table description of table portal_state_logs. Objects of this class serve as prototypes for rows in queries. */
  class PortalStateLogs(_tableTag: Tag) extends Table[PortalStateLogsRow](_tableTag, Some("intel"), "portal_state_logs") {
    def * = (id, portalId, owner, level, health, team, mitigation, resCount, resonators, mods, createdAt, artifact) <> (PortalStateLogsRow.tupled, PortalStateLogsRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (id.?, portalId.?, owner, level.?, health.?, team.?, mitigation, resCount.?, resonators, mods, createdAt.?, artifact).shaped.<>({r=>import r._; _1.map(_=> PortalStateLogsRow.tupled((_1.get, _2.get, _3, _4.get, _5.get, _6.get, _7, _8.get, _9, _10, _11.get, _12)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))
    
    /** Database column id DBType(serial), AutoInc, PrimaryKey */
    val id: Column[Int] = column[Int]("id", O.AutoInc, O.PrimaryKey)
    /** Database column portal_id DBType(int4) */
    val portalId: Column[Int] = column[Int]("portal_id")
    /** Database column owner DBType(varchar), Length(2147483647,true), Default(None) */
    val owner: Column[Option[String]] = column[Option[String]]("owner", O.Length(2147483647,varying=true), O.Default(None))
    /** Database column level DBType(int2) */
    val level: Column[Short] = column[Short]("level")
    /** Database column health DBType(int2) */
    val health: Column[Short] = column[Short]("health")
    /** Database column team DBType(bpchar), Length(1,false) */
    val team: Column[String] = column[String]("team", O.Length(1,varying=false))
    /** Database column mitigation DBType(int2), Default(None) */
    val mitigation: Column[Option[Short]] = column[Option[Short]]("mitigation", O.Default(None))
    /** Database column res_count DBType(int2) */
    val resCount: Column[Short] = column[Short]("res_count")
    /** Database column resonators DBType(jsonb), Length(2147483647,false), Default(None) */
    val resonators: Column[Option[String]] = column[Option[String]]("resonators", O.Length(2147483647,varying=false), O.Default(None))
    /** Database column mods DBType(jsonb), Length(2147483647,false), Default(None) */
    val mods: Column[Option[String]] = column[Option[String]]("mods", O.Length(2147483647,varying=false), O.Default(None))
    /** Database column created_at DBType(timestamp) */
    val createdAt: Column[java.sql.Timestamp] = column[java.sql.Timestamp]("created_at")
    /** Database column artifact DBType(jsonb), Length(2147483647,false), Default(None) */
    val artifact: Column[Option[String]] = column[Option[String]]("artifact", O.Length(2147483647,varying=false), O.Default(None))
    
    /** Foreign key referencing Portals (database name portal_state_logs_portal_id_fkey) */
    lazy val portalsFk = foreignKey("portal_state_logs_portal_id_fkey", portalId, Portals)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.Cascade)
  }
  /** Collection-like TableQuery object for table PortalStateLogs */
  lazy val PortalStateLogs = new TableQuery(tag => new PortalStateLogs(tag))
  
  /** Entity class storing rows of table SentinelTasks
   *  @param id Database column id DBType(serial), AutoInc, PrimaryKey
   *  @param priority Database column priority DBType(int2)
   *  @param tag Database column tag DBType(varchar), Length(64,true)
   *  @param script Database column script DBType(text), Length(2147483647,true)
   *  @param waitAfter Database column wait_after DBType(int8)
   *  @param expiredAt Database column expired_at DBType(timestamp), Default(None)
   *  @param createdAt Database column created_at DBType(timestamp) */
  case class SentinelTasksRow(id: Int, priority: Short, tag: String, script: String, waitAfter: Long, expiredAt: Option[java.sql.Timestamp] = None, createdAt: java.sql.Timestamp)
  /** GetResult implicit for fetching SentinelTasksRow objects using plain SQL queries */
  implicit def GetResultSentinelTasksRow(implicit e0: GR[Int], e1: GR[Short], e2: GR[String], e3: GR[Long], e4: GR[Option[java.sql.Timestamp]], e5: GR[java.sql.Timestamp]): GR[SentinelTasksRow] = GR{
    prs => import prs._
    SentinelTasksRow.tupled((<<[Int], <<[Short], <<[String], <<[String], <<[Long], <<?[java.sql.Timestamp], <<[java.sql.Timestamp]))
  }
  /** Table description of table sentinel_tasks. Objects of this class serve as prototypes for rows in queries. */
  class SentinelTasks(_tableTag: Tag) extends Table[SentinelTasksRow](_tableTag, Some("intel"), "sentinel_tasks") {
    def * = (id, priority, tag, script, waitAfter, expiredAt, createdAt) <> (SentinelTasksRow.tupled, SentinelTasksRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (id.?, priority.?, tag.?, script.?, waitAfter.?, expiredAt, createdAt.?).shaped.<>({r=>import r._; _1.map(_=> SentinelTasksRow.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6, _7.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))
    
    /** Database column id DBType(serial), AutoInc, PrimaryKey */
    val id: Column[Int] = column[Int]("id", O.AutoInc, O.PrimaryKey)
    /** Database column priority DBType(int2) */
    val priority: Column[Short] = column[Short]("priority")
    /** Database column tag DBType(varchar), Length(64,true) */
    val tag: Column[String] = column[String]("tag", O.Length(64,varying=true))
    /** Database column script DBType(text), Length(2147483647,true) */
    val script: Column[String] = column[String]("script", O.Length(2147483647,varying=true))
    /** Database column wait_after DBType(int8) */
    val waitAfter: Column[Long] = column[Long]("wait_after")
    /** Database column expired_at DBType(timestamp), Default(None) */
    val expiredAt: Column[Option[java.sql.Timestamp]] = column[Option[java.sql.Timestamp]]("expired_at", O.Default(None))
    /** Database column created_at DBType(timestamp) */
    val createdAt: Column[java.sql.Timestamp] = column[java.sql.Timestamp]("created_at")
    
    /** Index over (priority) (database name sentinel_tasks_idx00) */
    val index1 = index("sentinel_tasks_idx00", priority)
    /** Index over (tag) (database name sentinel_tasks_idx01) */
    val index2 = index("sentinel_tasks_idx01", tag)
  }
  /** Collection-like TableQuery object for table SentinelTasks */
  lazy val SentinelTasks = new TableQuery(tag => new SentinelTasks(tag))
}