import play.PlayScala

organization := "org.koiroha.bombaysapphire"

name := "ruli"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.6"

libraryDependencies ++= Seq(
  "org.postgresql" % "postgresql" % "9.3-1102-jdbc41",
  "com.typesafe.slick" %% "slick" % "2.1.+",
  "com.typesafe.slick" %% "slick-codegen" % "2.1.+",
  "com.typesafe.play" %% "play-slick" % "0.8.1",
  "org.scala-lang.modules" %% "scala-parser-combinators" % "latest.integration",
  "org.scala-lang.modules" %% "scala-xml" %  "latest.integration",
  "org.commonjava.googlecode.markdown4j" % "markdown4j" % "latest.integration",
  "org.slf4j" % "slf4j-api" % "latest.integration",
  jdbc,
  anorm,
  cache,
  ws
)

// code generation task
lazy val slick = TaskKey[Seq[File]]("gen-tables")

lazy val slickCodeGenTask = (sourceManaged, dependencyClasspath in Compile, runner in Compile, streams) map { (dir, cp, r, s) =>
  val outputDir = "app"
  val url = "jdbc:postgresql://localhost:5432/bombaysapphire"
  val jdbcDriver = "org.postgresql.Driver"
  val slickDriver = "scala.slick.driver.PostgresDriver"
  val pkg = "models"
  val user = "postgres"
  val password = "postgres"
  toError(r.run("scala.slick.codegen.SourceCodeGenerator", cp.files, Array(slickDriver, jdbcDriver, url, outputDir, pkg, user, password), s.log))
  val fname = outputDir + "/Tables.scala"
  Seq(file(fname))
}

slick <<= slickCodeGenTask
