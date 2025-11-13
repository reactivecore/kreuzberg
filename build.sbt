import xerial.sbt.Sonatype.GitHubHosting
import xerial.sbt.Sonatype.sonatypeCentralHost

// If there is a Tag starting with v, e.g. v0.3.0 use it as the build artefact version (e.g. 0.3.0)
val gitTag: Option[String] = sys.env.get("GITHUB_REF").flatMap { ref =>
  if (ref.startsWith("refs/tags/")) Some(ref.stripPrefix("refs/tags/"))
  else None
}

val versionTag = gitTag
  .filter(_.startsWith("v"))
  .map(_.stripPrefix("v"))

val snapshotVersion = "0.11-SNAPSHOT"
val artefactVersion = versionTag.getOrElse(snapshotVersion)

ThisBuild / version := artefactVersion

ThisBuild / scalaVersion := "3.7.4"

ThisBuild / scalacOptions += "-Xcheck-macros"
ThisBuild / scalacOptions += "-feature"
ThisBuild / scalacOptions ++= Seq("-rewrite", "-source", "3.4-migration")

ThisBuild / Compile / run / fork := true
ThisBuild / Test / run / fork    := true

ThisBuild / organization := "net.reactivecore"

ThisBuild / scalaJSStage := FullOptStage

val isIntelliJ = {
  val isIdea = sys.props.get("idea.managed").contains("true")
  if (isIdea) {
    println("Using IntelliJ workarounds. Do not publish")
  }
  isIdea
}

val scalaTagsVersion             = "0.13.1"
val scalatestVersion             = "3.2.19"
val logbackVersion               = "1.5.21"
val slf4jVersion                 = "2.0.17"
val scalaJsDomVersion            = "2.8.1"
val scalaJsWeakReferencesVersion = "1.0.0"
val scalaJsJavaTimeVersion       = "2.5.0"
val scalaXmlVersion              = "2.4.0"
val circeVersion                 = "0.14.15"
val tapirVersion                 = "1.12.3"
val sttpVersion                  = "3.11.0"
val questVersion                 = "0.2.0"

ThisBuild / sonatypeCredentialHost := sonatypeCentralHost

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

val logsettings = libraryDependencies ++= Seq(
  "ch.qos.logback" % "logback-classic" % logbackVersion
)

lazy val testCore = (crossProject(JSPlatform, JVMPlatform, NativePlatform) in file("test-core"))
  .settings(
    name           := "test-core",
    libraryDependencies ++= Seq(
      "org.scalatest" %%% "scalatest"          % scalatestVersion,
      "org.scalatest" %%% "scalatest-flatspec" % scalatestVersion
    ),
    publish / skip := true,
    publishLocal   := {}
  )
  .jvmSettings(
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % logbackVersion
    )
  )

lazy val jsDomMock = (crossProject(JVMPlatform, NativePlatform) in file("js-dom-mock"))
  .settings(
    name := "kreuzberg-scalajs-dom-mock",
    publishSettings
  )
  .dependsOn(
    testCore % Test
  )

/** Defines a component. */
lazy val lib = (crossProject(JSPlatform, JVMPlatform, NativePlatform) in file("lib"))
  .settings(
    name := "kreuzberg",
    publishSettings
  )
  .dependsOn(
    testCore % Test
  )
  .jvmConfigure(_.dependsOn(jsDomMock.jvm))
  .nativeConfigure(_.dependsOn(jsDomMock.native))
  .jsSettings(
    libraryDependencies ++= Seq(
      "org.scala-js"  %%% "scalajs-dom"            % scalaJsDomVersion,
      ("org.scala-js" %%% "scalajs-weakreferences" % scalaJsWeakReferencesVersion).cross(CrossVersion.for3Use2_13)
    )
  )

/** Naive simple engine implementation (mutable like hell, but small in Size) */
lazy val engineNaive = (project in file("engine-naive"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(lib.js, testCore.js % Test)
  .settings(
    name := "kreuzberg-engine-naive",
    publishSettings
  )

lazy val xml = (crossProject(JSPlatform, JVMPlatform, NativePlatform) in file("xml"))
  .settings(
    name := "kreuzberg-xml",
    libraryDependencies ++= Seq(
      "org.scala-lang.modules"  %% "scala-xml" % scalaXmlVersion,
      "org.scala-lang.modules" %%% "scala-xml" % scalaXmlVersion
    ),
    publishSettings
  )
  .dependsOn(lib, testCore % Test)

lazy val scalatags = (crossProject(JSPlatform, JVMPlatform, NativePlatform) in file("scalatags"))
  .settings(
    name := "kreuzberg-scalatags",
    libraryDependencies ++= Seq(
      "com.lihaoyi" %%% "scalatags" % scalaTagsVersion
    ),
    publishSettings
  )
  .dependsOn(lib, testCore % Test)

// Note: rpc doesn't depend on lib (because we do not need it)
lazy val rpc = (crossProject(JSPlatform, JVMPlatform, NativePlatform) in file("rpc"))
  .settings(
    name               := "kreuzberg-rpc",
    libraryDependencies ++= Seq(
      "io.circe" %%% "circe-core"   % circeVersion,
      "io.circe" %%% "circe-parser" % circeVersion
    ),
    evictionErrorLevel := Level.Warn,
    publishSettings
  )
  .jsSettings(
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % scalaJsDomVersion
    )
  )
  .dependsOn(
    testCore % Test
  )

lazy val extras = (crossProject(JSPlatform, JVMPlatform, NativePlatform) in file("extras"))
  .settings(
    name := "kreuzberg-extras",
    publishSettings
  )
  .dependsOn(lib % "compile->compile;test->test", scalatags, testCore % Test)

// Tapir/Loom based Mini Server
lazy val miniserver = (project in file("miniserver"))
  .settings(
    name := "kreuzberg-miniserver",
    libraryDependencies ++= Seq(
      "org.slf4j"                      % "slf4j-api"                % slf4jVersion,
      "com.softwaremill.sttp.tapir"   %% "tapir-netty-server-sync"  % tapirVersion,
      "com.softwaremill.sttp.tapir"   %% "tapir-swagger-ui-bundle"  % tapirVersion,
      "com.softwaremill.sttp.tapir"   %% "tapir-json-circe"         % tapirVersion,
      "com.softwaremill.sttp.tapir"   %% "tapir-prometheus-metrics" % tapirVersion,
      "com.softwaremill.sttp.client3" %% "core"                     % sttpVersion % Test,
      "net.reactivecore"              %% "quest"                    % questVersion,
      "org.webjars"                    % "jquery"                   % "3.7.1"     % Test // For testing Webjar Loader
    ),
    publishSettings
  )
  .dependsOn(lib.jvm, scalatags.jvm, rpc.jvm, testCore.jvm % Test)

lazy val examples = (crossProject(JSPlatform, JVMPlatform) in file("examples"))
  .settings(
    name           := "examples",
    publish / skip := true,
    publishLocal   := {}
  )
  .jsSettings(
    Compile / fastOptJS / artifactPath := baseDirectory.value / "target/client_bundle/client/fast/main.js",
    scalaJSUseMainModuleInitializer    := true,
    // Important, no source maps on full link js
    Compile / fullLinkJS / scalaJSLinkerConfig ~= (_.withSourceMap(false))
  )
  .jvmSettings(logsettings)
  .jvmConfigure(_.dependsOn(miniserver))
  .jsConfigure(_.dependsOn(engineNaive))
  .dependsOn(lib, xml, scalatags, extras, rpc, testCore % Test)
  .jsEnablePlugins(
    ScalaJSWeb
  )

lazy val runner = (project in file("runner"))
  .settings(
    // Attaching to Debug Output
    Compile / compile   := (Compile / compile).dependsOn(examples.js / Compile / fastOptJS).value,
    Compile / mainClass := Some("kreuzberg.examples.showcase.DebugMain"),
    publish / skip      := true,
    publishLocal        := {}
  )
  .dependsOn(examples.jvm, testCore.jvm % Test)

// SBT / JS Pipeline seems to break IntelliJs multi project workign
// We need it for releasing or inside SBT anyway.
// Perhaps related to https://github.com/vmunier/sbt-web-scalajs/issues/169
def maybeEnableSbtWebPipeline(p: Project): Project = {
  if (isIntelliJ) {
    p
  } else {
    p
      .enablePlugins(SbtWeb, JavaAppPackaging)
      .settings(
        Assets / pipelineStages := Seq(scalaJSPipeline, brotli),
        scalaJSProjects         := Seq(examples.js),
        (Runtime / managedClasspath) += (Assets / packageBin).value
      )
  }
}

lazy val runnerProd = maybeEnableSbtWebPipeline {
  (project in file("runner-prod"))
    .settings(
      Compile / mainClass := Some("kreuzberg.examples.showcase.ProdMain"),
      publish / skip      := true
    )
    .dependsOn(examples.jvm)
}

lazy val root = (project in file("."))
  .settings(
    name           := "kreuzberg-root",
    publish / skip := true,
    test           := {},
    run            := (runner / Compile / run).evaluated
  )
  .aggregate(
    testCore.js,
    testCore.jvm,
    testCore.native,
    lib.js,
    lib.jvm,
    lib.native,
    engineNaive,
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
    rpc.native,
    jsDomMock.jvm,
    jsDomMock.native,
    runner,
    runnerProd
  )
