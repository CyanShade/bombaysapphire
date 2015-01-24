import sbt._
import Keys._
import scala._
 
object Build extends sbt.Build {
  private[this] val standardSettings = Defaults.defaultSettings ++ Seq(
    organization := "org.koiroha.bombaysapphire",
    scalaVersion := "2.11.4",
    scalacOptions ++= Seq("-unchecked", "-feature", "-deprecation", "-encoding", "UTF-8"),
    javacOptions ++= Seq("-encoding", "UTF-8", "-source", "1.8", "-target", "1.8"),
    libraryDependencies ++= Seq(
      "org.slf4j" %  "slf4j-log4j12" % "latest.integration"
    )
  )

  lazy val root = Project(
    id = "bombaysapphire", base = file(".")
  ) aggregate(share, sentinel, garuda)

  lazy val share = Project(
    id = "share",
    base = file("share"),
    settings = standardSettings
  )

  lazy val sentinel = Project(
    id = "sentinel",
    base = file("sentinel"),
    settings = standardSettings ++ Seq(collectJarsTask)
  ) dependsOn(share)

  lazy val garuda = Project(
    id = "garuda",
    base = file("garuda"),
    settings = standardSettings ++ Seq(collectJarsTask)
  ) dependsOn(share)

  val collectJars = TaskKey[Unit]("collect-jars", "collect dependent jar files to target/lib")
  val collectJarsTask = collectJars := {
    val dir = "target/lib"
    val file = s"${name.value}_2.11-${version.value}.jar"
    val src = new File(s"${name.value}/target/scala-2.11/$file")
    println(s"COPY: $src -> $dir/$file")
    org.apache.ivy.util.FileUtil.copy(src, new File(s"$dir/$file"), null)
    (dependencyClasspath in Compile) map { paths =>
      paths.map { path =>
        val jar = path.data
        val dist = new File(s"$dir/${jar.getName}")
        org.apache.ivy.util.FileUtil.copy(jar, dist, null)
        println(s"$jar -> $dist")
        dist
      }
    }
  }
 
}
