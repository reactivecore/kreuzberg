// If there is a Tag starting with v, e.g. v0.3.0 use it as the build artefact version (e.g. 0.3.0)
val versionTag = sys.env
  .get("CI_COMMIT_TAG")
  .filter(_.startsWith("v"))
  .map(_.stripPrefix("v"))

val snapshotVersion = "0.5-SNAPSHOT"
val artefactVersion = versionTag.getOrElse(snapshotVersion)

ThisBuild / version := artefactVersion

ThisBuild / scalaVersion := "3.2.2"

ThisBuild / scalacOptions += "-Xcheck-macros"
ThisBuild / scalacOptions += "-feature"
// ThisBuild / scalacOptions += "-explain"

ThisBuild / Compile / run / fork := true
ThisBuild / Test / run / fork    := true

ThisBuild / organization := "net.reactivecore"

val zioVersion       = "2.0.13"
val scalatagsVersion = "0.12.0"
val zioServerVersion = "3.0.0-RC1"

val publishSettings = Seq(
  publishTo           := {
    val nexus = "https://sonatype.rcxt.de/repository/reactivecore/"
    if (isSnapshot.value)
      Some("snapshots" at nexus)
    else
      Some("releases" at nexus)
  },
  publishMavenStyle   := true,
  credentials += {
    for {
      username <- sys.env.get("SONATYPE_USERNAME")
      password <- sys.env.get("SONATYPE_PASSWORD")
    } yield {
      Credentials("Sonatype Nexus Repository Manager", "sonatype.rcxt.de", username, password)
    }
  }.getOrElse(
    Credentials(Path.userHome / ".sbt" / "sonatype.rcxt.de.credentials")
  ),
  publish / test      := {},
  publishLocal / test := {}
)

val testSettings = libraryDependencies ++= Seq(
  "org.scalatest" %%% "scalatest"          % "3.2.14" % Test,
  "org.scalatest" %%% "scalatest-flatspec" % "3.2.14" % Test
)

val logsettings = libraryDependencies ++= Seq(
  "ch.qos.logback" % "logback-classic" % "1.4.5"
)

/** Defines a component. */
lazy val lib = (crossProject(JSPlatform, JVMPlatform) in file("lib"))
  .settings(
    name         := "kreuzberg",
    publishSettings,
    testSettings,
    libraryDependencies += (
      "dev.zio" %%% "zio" % zioVersion % Provided
    )
  )
  .jsSettings(
    libraryDependencies ++= Seq(
      "org.scala-js"  %%% "scalajs-dom"            % "2.3.0",
      ("org.scala-js" %%% "scalajs-weakreferences" % "1.0.0").cross(CrossVersion.for3Use2_13)
    )
  )

/** Common codes for Engine */
lazy val engineCommon = (crossProject(JSPlatform, JVMPlatform) in file("engine-common"))
  .settings(
    name := "kreuzberg-engine-common",
    publishSettings,
    testSettings
  )
  .dependsOn(lib)

/** Naive simple engine implementation (mutable like hell, but small in Size) */
lazy val engineNaive = (project in file("engine-naive"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(lib.js, engineCommon.js)
  .settings(
    name := "kreuzberg-engine-naive",
    publishSettings,
    testSettings
  )

/** ZIO Based Engine (slower, bigger size, cleaner) */
lazy val engineZio = (project in file("engine-zio"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(lib.js, engineCommon.js)
  .settings(
    name := "kreuzberg-engine-zio",
    publishSettings,
    libraryDependencies ++= Seq(
      "dev.zio"           %%% "zio"                  % zioVersion,
      "dev.zio"           %%% "zio-streams"          % zioVersion,
      "org.scala-js"      %%% "scalajs-dom"          % "2.3.0",
      "io.github.cquiroz" %%% "scala-java-time"      % "2.5.0",
      "io.github.cquiroz" %%% "scala-java-time-tzdb" % "2.5.0"
    ),
    testSettings
  )

lazy val xml = (crossProject(JSPlatform, JVMPlatform) in file("xml"))
  .settings(
    name := "kreuzberg-xml",
    libraryDependencies ++= Seq(
      "org.scala-lang.modules"  %% "scala-xml" % "2.1.0",
      "org.scala-lang.modules" %%% "scala-xml" % "2.1.0"
    ),
    publishSettings,
    testSettings
  )
  .dependsOn(lib, engineCommon % Test)

lazy val scalatags = (crossProject(JSPlatform, JVMPlatform) in file("scalatags"))
  .settings(
    name := "kreuzberg-scalatags",
    libraryDependencies ++= Seq(
      "com.lihaoyi" %%% "scalatags" % "0.12.0"
    ),
    publishSettings,
    testSettings
  )
  .dependsOn(lib, engineCommon % Test)

lazy val rpc = (crossProject(JSPlatform, JVMPlatform) in file("rpc"))
  .settings(
    name               := "kreuzberg-rpc",
    libraryDependencies ++= Seq(
      "com.lihaoyi" %%% "upickle" % "3.0.0-M2",
      "dev.zio"     %%% "zio"     % zioVersion % Provided
    ),
    evictionErrorLevel := Level.Warn,
    publishSettings,
    testSettings
  )
  .dependsOn(lib)

lazy val extras = (crossProject(JSPlatform, JVMPlatform) in file("extras"))
  .settings(
    name := "kreuzberg-extras",
    publishSettings,
    testSettings
  )
  .dependsOn(lib, scalatags)

lazy val miniserver = (project in file("miniserver"))
  .settings(
    name := "kreuzberg-miniserver",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-http"           % zioServerVersion,
      "dev.zio" %% "zio-logging-slf4j2" % "2.1.10"
    ),
    publishSettings,
    testSettings
  )
  .dependsOn(lib.jvm, scalatags.jvm, rpc.jvm)

lazy val examples = (crossProject(JSPlatform, JVMPlatform) in file("examples"))
  .settings(
    name            := "examples",
    publishArtifact := false,
    publish         := {},
    publishLocal    := {}
  )
  .jsSettings(
    // Moving JavaScript to a place, where we can easily find it by the server
    Compile / fastOptJS / artifactPath := baseDirectory.value / "target/client_bundle/client/fast/main.js",
    Compile / fullOptJS / artifactPath := baseDirectory.value / "target/client_bundle/client/opt/main.js",
    scalaJSUseMainModuleInitializer    := true
  )
  .jvmSettings(logsettings)
  .jvmConfigure(_.dependsOn(miniserver))
  .jsConfigure(_.dependsOn(engineNaive))
  .dependsOn(lib, xml, scalatags, extras, rpc)

// ZIO Build of Examples
lazy val examplesZio = (crossProject(JSPlatform, JVMPlatform) in file("examples-zio"))
  .settings(
    name            := "examples-zio",
    publishArtifact := false,
    publish         := {},
    publishLocal    := {}
  )
  .jsSettings(
    // Moving JavaScript to a place, where we can easily find it by the server
    Compile / fastOptJS / artifactPath := baseDirectory.value / "target/client_bundle/client/fast/main.js",
    Compile / fullOptJS / artifactPath := baseDirectory.value / "target/client_bundle/client/opt/main.js",
    scalaJSUseMainModuleInitializer    := true,
    Compile / mainClass                := Some("kreuzberg.examples.showcasezio.Main")
  )
  .jvmSettings(
    Compile / mainClass := Some("kreuzberg.examples.showcase.ServerMainZio")
  )
  .dependsOn(examples)
  .jsConfigure(_.dependsOn(engineZio))

lazy val runner = (project in file("runner"))
  .settings(
    Compile / compile         := (Compile / compile).dependsOn(examples.js / Compile / fastOptJS).value,
    Compile / run / mainClass := (examples.jvm / Compile / run / mainClass).value,
    reStartArgs               := Seq("serve"),
    publish                   := {},
    publishLocal              := {}
  )
  .dependsOn(examples.jvm)

lazy val runnerZio = (project in file("runner-zio"))
  .settings(
    Compile / compile         := (Compile / compile).dependsOn(examplesZio.js / Compile / fastOptJS).value,
    Compile / run / mainClass := (examplesZio.jvm / Compile / run / mainClass).value,
    reStartArgs               := Seq("serve"),
    publish                   := {},
    publishLocal              := {}
  )
  .dependsOn(examplesZio.jvm)

lazy val root = (project in file("."))
  .settings(
    name            := "kreuzberg-root",
    publish         := {},
    publishLocal    := {},
    test            := {},
    publishArtifact := false
  )
  .aggregate(
    lib.js,
    lib.jvm,
    engineNaive,
    engineZio,
    engineCommon.js,
    engineCommon.jvm,
    xml.js,
    xml.jvm,
    scalatags.js,
    scalatags.jvm,
    extras.js,
    extras.jvm,
    miniserver,
    examples.js,
    examples.jvm,
    examplesZio.js,
    examplesZio.jvm,
    rpc.js,
    rpc.jvm
  )
