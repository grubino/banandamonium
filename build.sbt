name := """banandamonium-api"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  "org.reactivemongo"     %%      "play2-reactivemongo" %       "0.11.2.play24",
  cache,
  ws
)
