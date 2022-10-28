ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.2.1"

ThisBuild / Compile / run / fork := true
ThisBuild / Test / run / fork    := true

lazy val lib = (project in file("lib"))
  .settings(
    name := "kreuzberg",
    libraryDependencies ++= Seq(
      "com.lihaoyi"   %% "scalatags"   % "0.11.1",
      "com.lihaoyi"  %%% "scalatags"   % "0.11.1",
      "org.scala-js" %%% "scalajs-dom" % "2.3.0" // Bricht das?
    )
  )
  .enablePlugins(ScalaJSPlugin)

lazy val extras = (project in file("extras"))
  .settings(
    name := "extras"
  )
  .dependsOn(lib)
  .enablePlugins(ScalaJSPlugin)

lazy val miniserver = (project in file("miniserver"))
  .settings(
    name := "miniserver",
    libraryDependencies ++= Seq(
      "io.d11"                     %% "zhttp"           % "2.0.0-RC10",
      "ch.qos.logback"              % "logback-classic" % "1.2.11",
      "com.typesafe.scala-logging" %% "scala-logging"   % "3.9.4"
    )
  )

lazy val examples = (project in file("examples"))
  .settings(
    name := "examples"
  )
  .dependsOn(lib, extras)
  .enablePlugins(ScalaJSPlugin)

lazy val root = (project in file("."))
  .settings(
    name            := "kreuzberg-root",
    publish         := {},
    publishLocal    := {},
    test            := {},
    publishArtifact := false
  )
  .aggregate(
    lib,
    extras,
    miniserver,
    examples
  )
