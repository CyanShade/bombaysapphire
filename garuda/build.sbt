
organization := "org.koiroha.bombaysapphire"

name := "garuda"

version := "1.0.0-SNAPSHOT"

scalaVersion := "2.11.4"

scalacOptions ++= Seq("-unchecked", "-feature", "-deprecation", "-encoding", "UTF-8")

javacOptions ++= Seq("-encoding", "UTF-8", "-source", "1.8", "-target", "1.8")

libraryDependencies ++= Seq(
  "org.koiroha.bombaysapphire" %% "share" % "1.0.0-SNAPSHOT",
  "com.twitter" %% "finagle-http" % "6.+",
  "com.typesafe.slick" %% "slick" % "2.1.+",
  "com.typesafe.slick" %% "slick-codegen" % "2.1.+",
  "org.postgresql" % "postgresql" % "9.3-1102-jdbc41",
  "org.json4s" %% "json4s-native" % "3.2.+",
  "ch.hsr" % "geohash" % "latest.integration",
  "org.slf4j" %  "slf4j-log4j12" % "latest.integration"
)

