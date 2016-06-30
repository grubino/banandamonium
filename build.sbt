name := """banandamonium-api"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.7"

resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"
libraryDependencies ++= Seq(
  "org.reactivemongo"     %% "play2-reactivemongo"      % "0.11.13",
  specs2 % Test,
  cache,
  ws,
  "jp.t2v"                %% "play2-auth"               % "0.14.2",
  "org.scalaz"            %% "scalaz-core"              % "7.1.1"
)
