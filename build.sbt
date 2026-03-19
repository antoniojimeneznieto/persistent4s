val scala3Version = "3.8.2"

lazy val root = project
  .in(file("."))
  .enablePlugins(Smithy4sCodegenPlugin)
  .settings(
    name := "dcb_test",
    version := "0.1.0-SNAPSHOT",

    scalaVersion := scala3Version,

    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-effect" % "3.5.7",
      "com.disneystreaming.smithy4s" %% "smithy4s-http4s" % smithy4sVersion.value,
      "com.disneystreaming.smithy4s" %% "smithy4s-http4s-swagger" % smithy4sVersion.value,
      "org.http4s" %% "http4s-ember-server" % "0.23.30",
      "org.tpolecat" %% "skunk-core" % "0.6.5",
      /*"org.tpolecat" %% "doobie-core" % "1.0.0-RC12",
      "org.tpolecat" %% "doobie-postgres" % "1.0.0-RC12",
      "org.tpolecat" %% "doobie-postgres-circe" % "1.0.0-RC12",
      "org.tpolecat" %% "doobie-hikari" % "1.0.0-RC12", */
      "org.tpolecat" %% "skunk-circe" % "0.6.5",
      "io.circe" %% "circe-core" % "0.14.10",
      "io.circe" %% "circe-generic" % "0.14.10",
      "io.circe" %% "circe-parser" % "0.14.10",
      "com.github.pureconfig" %% "pureconfig-core" % "0.17.1",
      "org.scalameta" %% "munit" % "1.2.4" % Test
    )
  )
