
organization := "org.koiroha"

name := "bombaysapphire"

version := "1.0.0-SNAPSHOT"

scalaVersion := "2.11.4"

scalacOptions ++= Seq("-unchecked", "-feature", "-deprecation", "-encoding", "UTF-8")

javacOptions ++= Seq("-encoding", "UTF-8")

libraryDependencies ++= Seq(
  "com.twitter" %% "finagle-http" % "6.24.+",
  "com.typesafe.akka" %% "akka-remote" % "2.3.8",
  "com.typesafe.slick" %% "slick" % "2.1.0",
  "com.typesafe.slick" %% "slick-codegen" % "2.1.0",
  "org.postgresql" % "postgresql" % "9.3-1102-jdbc41",
  "org.json4s" %% "json4s-native" % "3.2.11",
  "ch.hsr" % "geohash" % "1.0.10",
  "org.scala-lang.modules" %% "scala-parser-combinators" % "1.+",
  "org.scala-lang.modules" %% "scala-xml" %  "1.+",
  "org.apache.spark" % "spark-mllib_2.11" % "1.+",
  "org.slf4j" %  "slf4j-log4j12" % "1.7.+"
)

TaskKey[Seq[java.io.File]]("collect-jars") <<=
  ( dependencyClasspath in Compile ) map { paths =>
    paths.map { path =>
      val jar = path.data
      val dist = new File("target/lib/"+jar.getName)
      org.apache.ivy.util.FileUtil.copy(jar,dist,null)
      dist
    }
  }
