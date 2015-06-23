name := "picfg"

version := "1.0"

scalaVersion := "2.11.6"

libraryDependencies ++= Seq(
  "sodium"         %% "sodium"       % "1.0",
  "com.jcraft"     % "jsch"          % "0.1.53",
  "org.scala-lang" % "scala-reflect" % scalaVersion.value
)


resolvers += Resolver.bintrayRepo("j-keck", "maven") // for sodium