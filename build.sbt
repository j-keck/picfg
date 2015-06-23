name := "picfg"

version := "1.0"

scalaVersion := "2.11.6"

libraryDependencies ++= Seq(

  "com.jcraft"     % "jsch"          % "0.1.53",
  "org.scala-lang" % "scala-reflect" % scalaVersion.value
)
