
organization := "org.koiroha"

name := "bombaysapphire"

scalaVersion := "2.11.4"

libraryDependencies ++= Seq(
  "com.twitter" %% "finagle-http" % "6.24.0",
  "com.typesafe.slick" %% "slick" % "2.1.0",
  "com.typesafe.slick" %% "slick-codegen" % "2.1.0",
  "org.postgresql" % "postgresql" % "9.3-1102-jdbc41",
  "org.slf4j" %  "slf4j-log4j12" % "1.7.7"
)
