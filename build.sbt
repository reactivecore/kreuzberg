import xerial.sbt.Sonatype.GitHubHosting

// If there is a Tag starting with v, e.g. v0.3.0 use it as the build artefact version (e.g. 0.3.0)
val versionTag = sys.env
  .get("CI_COMMIT_TAG")
  .filter(_.startsWith("v"))
  .map(_.stripPrefix("v"))

val snapshotVersion = "0.8-SNAPSHOT"
val artefactVersion = versionTag.getOrElse(snapshotVersion)

ThisBuild / version := artefactVersion

ThisBuild / scalaVersion := "3.3.3"

ThisBuild / scalacOptions += "-Xcheck-macros"
ThisBuild / scalacOptions += "-feature"

ThisBuild / Compile / run / fork := true
ThisBuild / Test / run / fork    := true

ThisBuild / organization := "net.reactivecore"

val zioVersion                   = "2.0.16"
val zioLoggingVersion            = "2.1.14"
val scalatagsVersion             = "0.12.0"
val zioHttpVersion               = "3.0.0-RC2"
val scalatestVersion             = "3.2.16"
val logbackVersion               = "1.4.7"
val scalaJsDomVersion            = "2.6.0"
val scalaJsWeakReferencesVersion = "1.0.0"
val scalaJsJavaTimeVersion       = "2.5.0"
val scalaXmlVersion              = "2.1.0"
val scalaTagsVersion             = "0.12.0"
val circeVersion                 = "0.14.6"

val isIntelliJ = {
  val isIdea = sys.props.get("idea.managed").contains("true")
  if (isIdea) {
    println("Using IntelliJ workarounds. Do not publish")
  }
  isIdea
}

def publishSettings = Seq(
  publishTo               := sonatypePublishToBundle.value,
  sonatypeBundleDirectory := (ThisBuild / baseDirectory).value / "target" / "sonatype-staging" / s"${version.value}",
  licenses                := Seq("APL2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
  homepage                := Some(url("https://github.com/reactivecore/kreuzberg")),
  sonatypeProjectHosting  := Some(GitHubHosting("reactivecore", "kreuzberg", "contact@reactivecore.de")),
  developers              := List(
    Developer(
      id = "nob13",
      name = "Norbert Schultz",
      email = "norbert.schultz@reactivecore.de",
      url = url("https://www.reactivecore.de")
    )
  ),
  publish / test          := {},
  publishLocal / test     := {}
)

usePgpKeyHex("77D0E9E04837F8CBBCD56429897A43978251C225")

val testSettings = libraryDependencies ++= Seq(
  "org.scalatest" %%% "scalatest"          % scalatestVersion % Test,
  "org.scalatest" %%% "scalatest-flatspec" % scalatestVersion % Test
)

val logsettings = libraryDependencies ++= Seq(
  "ch.qos.logback" % "logback-classic" % logbackVersion
)

/** Defines a component. */
lazy val lib = (crossProject(JSPlatform, JVMPlatform, NativePlatform) in file("lib"))
  .settings(
    name         := "kreuzberg",
    testSettings,
    publishSettings,
    libraryDependencies += (
      "dev.zio" %%% "zio" % zioVersion % (if (isIntelliJ) Compile else Provided)
    )
  )
  .jsSettings(
    libraryDependencies ++= Seq(
      "org.scala-js"  %%% "scalajs-dom"            % scalaJsDomVersion,
      ("org.scala-js" %%% "scalajs-weakreferences" % scalaJsWeakReferencesVersion).cross(CrossVersion.for3Use2_13)
    )
  )

/** Common codes for Engine */
lazy val engineCommon = (crossProject(JSPlatform, JVMPlatform, NativePlatform) in file("engine-common"))
  .settings(
    name := "kreuzberg-engine-common",
    testSettings,
    publishSettings
  )
  .dependsOn(lib)

/** Naive simple engine implementation (mutable like hell, but small in Size) */
lazy val engineNaive = (project in file("engine-naive"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(lib.js, engineCommon.js)
  .settings(
    name := "kreuzberg-engine-naive",
    testSettings,
    publishSettings
  )

lazy val xml = (crossProject(JSPlatform, JVMPlatform, NativePlatform) in file("xml"))
  .settings(
    name := "kreuzberg-xml",
    libraryDependencies ++= Seq(
      "org.scala-lang.modules"  %% "scala-xml" % scalaXmlVersion,
      "org.scala-lang.modules" %%% "scala-xml" % scalaXmlVersion
    ),
    testSettings,
    publishSettings
  )
  .dependsOn(lib, engineCommon % Test)

lazy val scalatags = (crossProject(JSPlatform, JVMPlatform, NativePlatform) in file("scalatags"))
  .settings(
    name := "kreuzberg-scalatags",
    libraryDependencies ++= Seq(
      "com.lihaoyi" %%% "scalatags" % scalaTagsVersion
    ),
    testSettings,
    publishSettings
  )
  .dependsOn(lib, engineCommon % Test)

lazy val rpc = (crossProject(JSPlatform, JVMPlatform, NativePlatform) in file("rpc"))
  .settings(
    name               := "kreuzberg-rpc",
    libraryDependencies ++= Seq(
      "io.circe" %%% "circe-core"   % circeVersion,
      "io.circe" %%% "circe-parser" % circeVersion,
      "dev.zio"  %%% "zio"          % zioVersion % (if (isIntelliJ) Compile else Provided)
    ),
    evictionErrorLevel := Level.Warn,
    testSettings,
    publishSettings
  )
  .dependsOn(lib)

lazy val extras = (crossProject(JSPlatform, JVMPlatform, NativePlatform) in file("extras"))
  .settings(
    name := "kreuzberg-extras",
    testSettings,
    publishSettings
  )
  .dependsOn(lib % "compile->compile;test->test", scalatags)

lazy val miniserver = (project in file("miniserver"))
  .settings(
    name := "kreuzberg-miniserver",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-http"           % zioHttpVersion,
      "dev.zio" %% "zio-logging-slf4j2" % zioLoggingVersion
    ),
    testSettings,
    publishSettings
  )
  .dependsOn(lib.jvm, scalatags.jvm, rpc.jvm)

lazy val examples = (crossProject(JSPlatform, JVMPlatform) in file("examples"))
  .settings(
    name            := "examples",
    publishArtifact := false,
    publish / skip  := true,
    publishLocal    := {},
    testSettings
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

lazy val runner = (project in file("runner"))
  .settings(
    Compile / compile         := (Compile / compile).dependsOn(examples.js / Compile / fastOptJS).value,
    Compile / run / mainClass := (examples.jvm / Compile / run / mainClass).value,
    reStartArgs               := Seq("serve"),
    publishArtifact           := false,
    publish / skip            := true,
    publishLocal              := {},
    testSettings
  )
  .dependsOn(examples.jvm)

lazy val root = (project in file("."))
  .settings(
    name           := "kreuzberg-root",
    publish / skip := true,
    test           := {}
  )
  .aggregate(
    lib.js,
    lib.jvm,
    lib.native,
    engineNaive,
    engineCommon.js,
    engineCommon.jvm,
    engineCommon.native,
    xml.js,
    xml.jvm,
    xml.native,
    scalatags.js,
    scalatags.jvm,
    scalatags.native,
    extras.js,
    extras.jvm,
    extras.native,
    miniserver,
    examples.js,
    examples.jvm,
    rpc.js,
    rpc.jvm,
    rpc.native
  )
