import xerial.sbt.Sonatype.GitHubHosting

// If there is a Tag starting with v, e.g. v0.3.0 use it as the build artefact version (e.g. 0.3.0)
val versionTag = sys.env
  .get("CI_COMMIT_TAG")
  .filter(_.startsWith("v"))
  .map(_.stripPrefix("v"))

val snapshotVersion = "0.10-SNAPSHOT"
val artefactVersion = versionTag.getOrElse(snapshotVersion)

ThisBuild / version := artefactVersion

ThisBuild / scalaVersion := "3.5.1"

ThisBuild / scalacOptions += "-Xcheck-macros"
ThisBuild / scalacOptions += "-feature"
ThisBuild / scalacOptions ++= Seq("-rewrite", "-source", "3.4-migration")

ThisBuild / Compile / run / fork := true
ThisBuild / Test / run / fork    := true

ThisBuild / organization := "net.reactivecore"

val scalaTagsVersion             = "0.13.1"
val scalatestVersion             = "3.2.19"
val logbackVersion               = "1.5.6"
val slf4jVersion                 = "2.0.13"
val scalaJsDomVersion            = "2.8.0"
val scalaJsWeakReferencesVersion = "1.0.0"
val scalaJsJavaTimeVersion       = "2.5.0"
val scalaXmlVersion              = "2.3.0"
val circeVersion                 = "0.14.9"
val tapirVersion                 = "1.10.14"
val questVersion                 = "0.2.0"

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

lazy val jsDomMock = (crossProject(JVMPlatform, NativePlatform) in file("js-dom-mock"))
  .settings(
    name := "kreuzberg-scalajs-dom-mock",
    testSettings,
    publishSettings
  )

/** Defines a component. */
lazy val lib = (crossProject(JSPlatform, JVMPlatform, NativePlatform) in file("lib"))
  .settings(
    name := "kreuzberg",
    testSettings,
    publishSettings
  )
  .jvmConfigure(_.dependsOn(jsDomMock.jvm))
  .nativeConfigure(_.dependsOn(jsDomMock.native))
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

// Note: rpc doesn't depend on lib (because we do not need it)
lazy val rpc = (crossProject(JSPlatform, JVMPlatform, NativePlatform) in file("rpc"))
  .settings(
    name               := "kreuzberg-rpc",
    libraryDependencies ++= Seq(
      "io.circe" %%% "circe-core"   % circeVersion,
      "io.circe" %%% "circe-parser" % circeVersion
    ),
    evictionErrorLevel := Level.Warn,
    testSettings,
    publishSettings
  )
  .jsSettings(
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % scalaJsDomVersion
    )
  )

lazy val extras = (crossProject(JSPlatform, JVMPlatform, NativePlatform) in file("extras"))
  .settings(
    name := "kreuzberg-extras",
    testSettings,
    publishSettings
  )
  .dependsOn(lib % "compile->compile;test->test", scalatags)

// Common Code for Server Side
lazy val miniserverCommon = (project in file("miniserver-common"))
  .settings(
    name := "kreuzberg-miniserver-common",
    testSettings,
    publishSettings
  )
  .dependsOn(lib.jvm, scalatags.jvm, rpc.jvm)

// Tapir/Loom based Mini Server
lazy val miniserverLoom = (project in file("miniserver-loom"))
  .settings(
    name := "kreuzberg-miniserver-loom",
    libraryDependencies ++= Seq(
      "org.slf4j"                    % "slf4j-api"               % slf4jVersion,
      "com.softwaremill.sttp.tapir" %% "tapir-netty-server-sync" % tapirVersion,
      "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % tapirVersion,
      "com.softwaremill.sttp.tapir" %% "tapir-json-circe"        % tapirVersion,
      "net.reactivecore"            %% "quest"                   % questVersion
    ),
    testSettings,
    publishSettings
  )
  .dependsOn(miniserverCommon)

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
  .jvmConfigure(_.dependsOn(miniserverLoom))
  .jsConfigure(_.dependsOn(engineNaive))
  .dependsOn(lib, xml, scalatags, extras, rpc)

lazy val runner = (project in file("runner"))
  .settings(
    Compile / compile         := (Compile / compile).dependsOn(examples.js / Compile / fastOptJS).value,
    Compile / run / mainClass := Some("kreuzberg.examples.showcase.Main"),
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
    miniserverLoom,
    miniserverCommon,
    examples.js,
    examples.jvm,
    rpc.js,
    rpc.jvm,
    rpc.native
  )
