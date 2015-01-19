
organization := "org.koiroha"

name := "bombaysapphire-sentinel"

version := "1.0.0-SNAPSHOT"

scalaVersion := "2.11.4"

scalacOptions ++= Seq("-unchecked", "-feature", "-deprecation", "-encoding", "UTF-8")

javacOptions ++= Seq("-encoding", "UTF-8", "-source", "1.8", "-target", "1.8")

libraryDependencies ++= Seq(
  "org.koiroha" %% "bombaysapphire-share" % "1.0.0-SNAPSHOT",
  "com.twitter" %% "finagle-http" % "6.+",
  "org.json4s" %% "json4s-native" % "3.2.+",
  "org.slf4j" %  "slf4j-log4j12" % "latest.integration"
)

TaskKey[Seq[java.io.File]]("collect-jars") <<= {
  (dependencyClasspath in Compile) map { paths =>
    paths.map { path =>
      val jar = path.data
      val dist = new File("target/lib/"+jar.getName)
      org.apache.ivy.util.FileUtil.copy(jar,dist,null)
      dist
    }
  }
}
