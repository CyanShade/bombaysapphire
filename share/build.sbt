
organization := "org.koiroha"

name := "bombaysapphire-share"

version := "1.0.0-SNAPSHOT"

scalaVersion := "2.11.4"

scalacOptions ++= Seq("-unchecked", "-feature", "-deprecation", "-encoding", "UTF-8")

javacOptions ++= Seq("-encoding", "UTF-8", "-source", "1.8", "-target", "1.8")

libraryDependencies ++= Seq(
  "com.twitter" %% "finagle-http" % "6.+",
  "org.scala-lang.modules" %% "scala-parser-combinators" % "latest.integration",
  "org.scala-lang.modules" %% "scala-xml" %  "latest.integration",
  "org.slf4j" %  "slf4j-log4j12" % "latest.integration"
)

TaskKey[Seq[java.io.File]]("collect-jars") <<= ( dependencyClasspath in Compile ) map { paths =>
    paths.map { path =>
      val jar = path.data
      val dist = new File("target/lib/"+jar.getName)
      org.apache.ivy.util.FileUtil.copy(jar,dist,null)
      dist
    }
  }
