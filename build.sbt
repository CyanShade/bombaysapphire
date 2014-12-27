
organization := "org.koiroha"

name := "bombaysapphire"

version := "1.0.0-SNAPSHOT"

scalaVersion := "2.11.4"

libraryDependencies ++= Seq(
  "com.twitter" %% "finagle-http" % "6.24.0",
  "com.typesafe.akka" %% "akka-remote" % "2.3.8",
  "com.typesafe.slick" %% "slick" % "2.1.0",
  "com.typesafe.slick" %% "slick-codegen" % "2.1.0",
  "org.postgresql" % "postgresql" % "9.3-1102-jdbc41",
  "org.json4s" %% "json4s-native" % "3.2.11",
  "ch.hsr" % "geohash" % "1.0.10",
  "org.slf4j" %  "slf4j-log4j12" % "1.7.7"
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
