package kreuzberg.miniserver

import java.nio.file.{Files, Path, Paths, StandardCopyOption}
import java.util.jar.{JarEntry, JarFile}
import scala.jdk.CollectionConverters.*
import scala.jdk.StreamConverters.*

/** Exports Index, JS and Assets to a directory */
class Exporter(config: DeploymentConfig) {

  final def main(args: Array[String]): Unit = {
    args.headOption match {
      case None      =>
        println(s"Expected directory")
        System.exit(1)
      case Some(dir) =>
        exportAll(Path.of(dir))
    }
  }

  /** Export everything into given directory. */
  def exportAll(dir: Path): Unit = {
    println(s"Exporting into ${dir}...")
    Files.createDirectories(dir)
    val renderedIndex = Index(config).pageHtml(None)
    Files.writeString(dir.resolve("index.html"), renderedIndex)
    val assetsDir     = dir.resolve("assets")
    config.assetPaths.assetPaths
      .filter { rp =>
        DeploymentType.isCompatible(rp.deploymentType, Some(DeploymentType.Production))
      }
      .foreach(rp => exportAssets(assetsDir, rp.path))
  }

  private def exportAssets(assetDir: Path, cp: AssetCandidatePath): Unit = {
    cp match
      case AssetCandidatePath.Resource(name, prefix) =>
        Option(getClass.getClassLoader.getResource(name)) match {
          case None        =>
            println(s"Skipping resource ${name}, not found")
          case Some(found) =>
            val asString = found.toString
            if (asString.startsWith("file:")) {
              val dir = Paths.get(found.toURI)
              copyPrefixedDirectory(dir, assetDir, prefix)
            } else if (asString.startsWith("jar:file:")) {
              val full        = asString.stripPrefix("jar:file:")
              val bang        = full.indexOf('!')
              val jarFileName = full.take(bang)
              val pathInJar   = full.drop(bang + 2)
              copyPrefixedJar(Paths.get(jarFileName), pathInJar, assetDir, prefix)
            } else {
              println(s"No support for URLs of the form ${asString}")
            }
        }
      case AssetCandidatePath.Directory(dir, prefix) =>
        val sourceDir = Paths.get(dir)
        copyPrefixedDirectory(sourceDir, assetDir, prefix)
  }

  private def copyPrefixedJar(jarFile: Path, pathInJar: String, to: Path, prefix: String): Unit = {
    val prefixedAssetDir = if (prefix.isEmpty) {
      to
    } else {
      to.resolve(prefix)
    }
    println(s"Copying ${jarFile} inJahr: ${pathInJar} to ${to} to ${prefixedAssetDir}")
    val file             = new java.util.jar.JarFile(jarFile.toFile)
    try {
      file.entries().asScala.foreach { jarEntry =>
        println(s"Testing ${jarEntry.getName}")
        if (jarEntry.getName.startsWith(pathInJar) && !jarEntry.isDirectory && !isBlacklisted(jarEntry.getName)) {
          val plainName = jarEntry.getName.stripPrefix(pathInJar).stripPrefix("/")
          val dstName   = prefixedAssetDir.resolve(plainName)
          niceCopyFromJar(file, jarEntry, dstName)
        }
      }
    } finally {
      file.close()
    }
  }

  private def niceCopyFromJar(file: JarFile, jarEntry: JarEntry, dstName: Path): Unit = {
    println(s"Copying ${jarEntry.getName} -> ${dstName}")
    Option(dstName.getParent).foreach { parent =>
      Files.createDirectories(parent)
    }
    val inputStream = file.getInputStream(jarEntry)
    Files.copy(inputStream, dstName, StandardCopyOption.REPLACE_EXISTING)
  }

  private val blacklistRegexes = config.produktionBlacklist.map(_.r)

  private def isBlacklisted(s: String): Boolean = {
    blacklistRegexes.exists(_.matches(s))
  }

  private def copyPrefixedDirectory(sourceDir: Path, to: Path, prefix: String): Unit = {
    val prefixedAssetDir = if (prefix.isEmpty) {
      to
    } else {
      to.resolve(prefix)
    }
    if (!Files.isDirectory(sourceDir)) {
      println(s"Skipping asset candidate path ${sourceDir}, not existing")
    } else {
      val files = Files.walk(sourceDir).toScala(Seq)
      files.foreach { sourceFile =>
        if (Files.isRegularFile(sourceFile) && !isBlacklisted(sourceFile.toString)) {
          val relativeSource = sourceDir.relativize(sourceFile)
          val destination    = prefixedAssetDir.resolve(relativeSource)
          niceCopy(sourceFile, destination)
        }
      }
    }
  }

  private def niceCopy(from: Path, to: Path): Unit = {
    Option(to.getParent).foreach(Files.createDirectories(_))
    println(s"Copy ${from} to ${to}")
    Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING)
  }
}
