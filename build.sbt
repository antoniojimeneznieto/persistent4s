ThisBuild / tlBaseVersion          := "0.1"
ThisBuild / tlMimaPreviousVersions := Set("0.1.1")
ThisBuild / scalaVersion           := "3.8.2"
ThisBuild / tlJdkRelease           := Some(17)
ThisBuild / organization           := "io.github.antoniojimeneznieto"
ThisBuild / organizationName       := "persistent4s"
ThisBuild / startYear              := Some(2026)
ThisBuild / licenses               := Seq(License.Apache2)
ThisBuild / developers             := List(
  tlGitHubDev("antoniojimeneznieto", "Antonio Jimenez"),
)

// Publishing
ThisBuild / sonatypeCredentialHost := xerial.sbt.Sonatype.sonatypeCentralHost

Global / onChangedBuildSource := ReloadOnSourceChanges
Global / semanticdbEnabled    := true // for metals

val commonSettings = List(
  scalafmtOnCompile            := false, // recommended in Scala 3
  testFrameworks               += new TestFramework("weaver.framework.CatsEffect"),
  Compile / run / fork         := true,
  Compile / run / javaOptions ++= Seq("--add-exports", "java.base/jdk.internal.vm=ALL-UNNAMED"),
  libraryDependencies         ++= List(
    // CE
    "org.typelevel" %% "cats-effect" % "3.7.0",
    "co.fs2"        %% "fs2-core"    % "3.12.0",

    // Postgres
    "org.tpolecat" %% "skunk-core" % "0.6.5",

    // Circe
    "io.circe" %% "circe-core"    % "0.14.15",
    "io.circe" %% "circe-generic" % "0.14.15",
    "io.circe" %% "circe-parser"  % "0.14.15",

    // Logging
    "org.typelevel" %% "log4cats-slf4j" % "2.7.1",

    // Telemetry
    "org.typelevel" %% "otel4s-core" % "0.13.0",

    // Testing
    "ch.qos.logback"     % "logback-classic" % "1.5.32" % Test,
    "org.testcontainers" % "postgresql"      % "1.21.4" % Test,
    "org.typelevel"     %% "weaver-cats"     % "0.11.3" % Test,
  ),
)

lazy val root = (project in file("."))
  .enablePlugins(NoPublishPlugin)
  .aggregate(core)

lazy val core = (project in file("modules/core"))
  .settings(commonSettings)
  .settings(name := "persistent4s")

addCommandAlias("lint", ";scalafmtAll ;scalafixAll --rules OrganizeImports")
