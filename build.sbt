name := """banandamonium-api"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.7"

resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"
libraryDependencies ++= Seq(
  "org.reactivemongo"     %%      "play2-reactivemongo" %       "0.11.2.play24",
  specs2 % Test,
  cache,
  ws,
  "org.scalaz" %% "scalaz-core" % "7.1.1"
)
