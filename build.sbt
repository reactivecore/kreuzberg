// If there is a Tag starting with v, e.g. v0.3.0 use it as the build artefact version (e.g. 0.3.0)
val versionTag = sys.env
  .get("CI_COMMIT_TAG")
  .filter(_.startsWith("v"))
  .map(_.stripPrefix("v"))

val snapshotVersion = "0.4-SNAPSHOT"
val artefactVersion = versionTag.getOrElse(snapshotVersion)

ThisBuild / version := artefactVersion

ThisBuild / scalaVersion := "3.2.1"

ThisBuild / scalacOptions += "-Xcheck-macros"

ThisBuild / Compile / run / fork := true
ThisBuild / Test / run / fork    := true

ThisBuild / organization := "net.reactivecore"

ThisBuild / evictionErrorLevel := Level.Warn // FIXME

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
  "org.scalatest" %%% "scalatest" % "3.2.14" % Test,
  "org.scalatest" %%% "scalatest-flatspec" % "3.2.14" % Test
)

val zioVersion = "2.0.6"

lazy val lib = (crossProject(JSPlatform, JVMPlatform) in file("lib"))
  .settings(
    name := "kreuzberg",
    publishSettings,
    testSettings
  )
  .jsSettings(
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % "2.3.0"
    )
  )

/** Naive simple engine implementation (mutable like hell, but small in Size) */
lazy val engineNaive = (project in file("engine-naive"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(lib.js)
  .settings(
    name := "kreuzberg-engine-naive",
    testSettings
  )

/** ZIO Based Engine (slower, bigger size, cleaner) */
lazy val engineZio = (project in file("engine-zio"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(lib.js)
  .settings(
    name := "kreuzberg-engine-zio",
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
  .dependsOn(lib)

lazy val scalatags = (crossProject(JSPlatform, JVMPlatform) in file("scalatags"))
  .settings(
    name := "kreuzberg-scalatags",
    libraryDependencies ++= Seq(
      "com.lihaoyi" %%% "scalatags" % "0.12.0"
    ),
    publishSettings,
    testSettings
  )
  .dependsOn(lib)

lazy val rpc = (crossProject(JSPlatform, JVMPlatform) in file("rpc"))
  .settings(
    name               := "kreuzberg-rpc",
    libraryDependencies ++= Seq(
      "com.lihaoyi" %%% "upickle" % "2.0.0"
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
      "dev.zio"                    %% "zio-http"        % "0.0.3",
      "ch.qos.logback"              % "logback-classic" % "1.4.5",
      "com.typesafe.scala-logging" %% "scala-logging"   % "3.9.5"
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
    publishLocal    := {},
    publishSettings
  )
  .jsSettings(
    // Moving JavaScript to a place, where we can easily find it by the server
    Compile / fastOptJS / artifactPath := baseDirectory.value / "target/client_bundle/client/fast/main.js",
    Compile / fullOptJS / artifactPath := baseDirectory.value / "target/client_bundle/client/opt/main.js",
    scalaJSUseMainModuleInitializer    := true
  )
  .jvmConfigure(_.dependsOn(miniserver))
  .jsConfigure(_.dependsOn(engineNaive))
  .dependsOn(lib, xml, scalatags, extras, rpc)

// ZIO Build of Examples
lazy val examplesZio = (crossProject(JSPlatform, JVMPlatform) in file("examples-zio"))
  .settings(
    name            := "examples-zio",
    publishArtifact := false,
    publish         := {},
    publishLocal    := {},
    publishSettings
  )
  .jsSettings(
    // Moving JavaScript to a place, where we can easily find it by the server
    Compile / fastOptJS / artifactPath := baseDirectory.value / "target/client_bundle/client/fast/main.js",
    Compile / fullOptJS / artifactPath := baseDirectory.value / "target/client_bundle/client/opt/main.js",
    scalaJSUseMainModuleInitializer    := true,
    Compile / mainClass := Some("kreuzberg.examples.showcasezio.Main")
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
    reStartArgs               := Seq("serve")
  )
  .dependsOn(examples.jvm)

lazy val runnerZio = (project in file("runner-zio"))
  .settings(
    Compile / compile         := (Compile / compile).dependsOn(examplesZio.js / Compile / fastOptJS).value,
    Compile / run / mainClass := (examplesZio.jvm / Compile / run / mainClass).value,
    reStartArgs               := Seq("serve")
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
