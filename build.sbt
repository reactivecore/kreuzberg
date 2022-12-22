// If there is a Tag starting with v, e.g. v0.3.0 use it as the build artefact version (e.g. 0.3.0)
val versionTag = sys.env
  .get("CI_COMMIT_TAG")
  .filter(_.startsWith("v"))
  .map(_.stripPrefix("v"))

val snapshotVersion = "0.3-SNAPSHOT"
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

val scalaTestDeps = Seq(
  "org.scalatest" %% "scalatest"          % "3.2.14" % Test,
  "org.scalatest" %% "scalatest-flatspec" % "3.2.14" % Test
)

lazy val lib = (crossProject(JSPlatform, JVMPlatform) in file("lib"))
  .settings(
    name := "kreuzberg",
    libraryDependencies ++= scalaTestDeps,
    publishSettings
  )
  .jsSettings(
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % "2.3.0"
    )
  )

lazy val xml = (crossProject(JSPlatform, JVMPlatform) in file("xml"))
  .settings(
    name := "kreuzberg-xml",
    libraryDependencies ++= Seq(
      "org.scala-lang.modules"  %% "scala-xml" % "2.1.0",
      "org.scala-lang.modules" %%% "scala-xml" % "2.1.0"
    ) ++ scalaTestDeps,
    publishSettings
  )
  .dependsOn(lib)

lazy val scalatags = (crossProject(JSPlatform, JVMPlatform) in file("scalatags"))
  .settings(
    name := "kreuzberg-scalatags",
    libraryDependencies ++= Seq(
      "com.lihaoyi" %%% "scalatags" % "0.12.0"
    ) ++ scalaTestDeps,
    publishSettings
  )
  .dependsOn(lib)

lazy val rpc = (crossProject(JSPlatform, JVMPlatform) in file("rpc"))
  .settings(
    name               := "kreuzberg-rpc",
    libraryDependencies ++= Seq(
      "com.lihaoyi" %%% "upickle" % "2.0.0"
    ) ++ scalaTestDeps,
    evictionErrorLevel := Level.Warn,
    publishSettings
  )
  .dependsOn(lib)

lazy val extras = (crossProject(JSPlatform, JVMPlatform) in file("extras"))
  .settings(
    name := "kreuzberg-extras",
    publishSettings
  )
  .dependsOn(lib, scalatags)

lazy val miniserver = (project in file("miniserver"))
  .settings(
    name := "kreuzberg-miniserver",
    libraryDependencies ++= Seq(
      "io.d11"                     %% "zhttp"           % "2.0.0-RC11",
      "ch.qos.logback"              % "logback-classic" % "1.4.5",
      "com.typesafe.scala-logging" %% "scala-logging"   % "3.9.5"
    ) ++ scalaTestDeps,
    publishSettings
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
  .dependsOn(lib, xml, scalatags, extras, rpc)

lazy val runner = (project in file("runner"))
  .settings(
    Compile / compile         := (Compile / compile).dependsOn(examples.js / Compile / fastOptJS).value,
    Compile / run / mainClass := (examples.jvm / Compile / run / mainClass).value,
    reStartArgs               := Seq("serve")
  )
  .dependsOn(examples.jvm)

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
    xml.js,
    xml.jvm,
    scalatags.js,
    scalatags.jvm,
    extras.js,
    extras.jvm,
    miniserver,
    examples.js,
    examples.jvm,
    rpc.js,
    rpc.jvm
  )
