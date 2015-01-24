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
    ),
    retrieveManaged := true
  )
  
  lazy val garuda = Project(
    id = "garuda",
    base = file("."),
    settings = standardSettings ++ Seq(
      collectJarsTask,
      collectJars <<= collectJars.dependsOn(Keys.`package` in Compile)
    )
  )

  val collectJars = TaskKey[Unit]("collect-jars", "collect dependent jar files to target/lib")
  val collectJarsTask = collectJars := {
    import scala.collection.JavaConversions._
    val dst = "target/lib"
    val file = s"${name.value}_2.11-${version.value}.jar"
    val src = new File(s"target/scala-2.11/$file")
    println(s"COPY: $file")
    org.apache.ivy.util.FileUtil.copy(src, new File(s"$dst/$file"), null)
    def copy(dir:File):Unit = dir.listFiles.foreach { f =>
      val name = f.getName
      if(f.isFile && name.endsWith(".jar")){
        val df = new File(s"$dst/$name")
        if(df.isFile){ 
          println(s"EXIST: $name")
        } else {
          println(s"COPY: $name")
          org.apache.ivy.util.FileUtil.copy(f, df, null)
        }
      } else if(f.isDirectory){
        copy(f)
      }
    }
    copy(new File("lib_managed"))
  }
 
}
