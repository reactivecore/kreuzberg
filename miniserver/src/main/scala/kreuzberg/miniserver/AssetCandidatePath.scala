package kreuzberg.miniserver

import java.io.{File, FileInputStream, IOException, InputStream}
import java.nio.file.{Files, Paths}
import java.security.{DigestInputStream, MessageDigest}
import java.util.Properties
import scala.language.implicitConversions
import scala.util.Using
import scala.util.control.NonFatal

sealed trait Location {

  /** Load the ressource. */
  def load(): InputStream

  /** Try to load the resource with Brotli */
  def brotli(): Option[InputStream]

  def hashSha256(): String = {
    Using(load()) { stream =>
      val sha256 = MessageDigest.getInstance("SHA-256")
      Using(new DigestInputStream(stream, sha256)) { dis =>
        val buf    = new Array[Byte](1024)
        while (dis.read(buf) >= 0) {}
        val digest = sha256.digest()
        toHex(digest)
      }.get
    }.get
  }

  private def toHex(ba: Array[Byte]): String = {
    val builder = new StringBuilder()
    ba.foreach { byte =>
      builder.append(String.format("%02x", Byte.box(byte)))
    }
    builder.result()
  }
}

object Location {
  case class File(file: java.io.File) extends Location {
    override def load(): InputStream = {
      new FileInputStream(file)
    }

    override def brotli(): Option[InputStream] = {
      val candidate = new java.io.File(file.getPath + ".br")
      if (candidate.exists()) {
        return Some(new FileInputStream(candidate))
      } else {
        None
      }
    }
  }

  case class ResourcePath(path: String, classLoader: ClassLoader) extends Location {
    override def load(): InputStream = {
      val stream = classLoader.getResourceAsStream(path)
      if (stream == null) {
        throw new IOException(s"Could not load resource ${path}")
      }
      stream
    }

    override def brotli(): Option[InputStream] = {
      val candidate = path + ".br"
      Option(classLoader.getResourceAsStream(candidate))
    }
  }
}

sealed trait AssetCandidatePath {
  def locate(name: String): Option[Location]
}

case class RestrictedAssetCandidatePath(
    deploymentType: Option[DeploymentType],
    path: AssetCandidatePath
)

object RestrictedAssetCandidatePath {
  implicit def fromCandidatePath(path: AssetCandidatePath): RestrictedAssetCandidatePath =
    RestrictedAssetCandidatePath(None, path)
}

object AssetCandidatePath {

  /** Default initialize a directory. */
  def apply(s: String): AssetCandidatePath.Directory = AssetCandidatePath.Directory(s)

  /**
   * Look in a resource
   *
   * @param name
   *   resource base path
   * @param prefix
   *   prefix which will be removed from any search file
   */
  case class Resource(name: String, prefix: String = "") extends AssetCandidatePath {
    private val classLoader = getClass.getClassLoader

    override def locate(path: String): Option[Location] = {
      val normalized = Paths.get(path).normalize().toString
      if (!normalized.startsWith(prefix)) {
        return None
      }
      val candidate  = name + normalized.stripPrefix(prefix)
      Option(classLoader.getResource(candidate)) match {
        case Some(_) =>
          Some(Location.ResourcePath(candidate, classLoader))
        case None    =>
          None
      }
    }
  }

  /**
   * Look in a directory
   * @param dir
   *   directory base path
   * @param prefix
   *   prefix which will be removed from any search file
   */
  case class Directory(dir: String, prefix: String = "") extends AssetCandidatePath {
    private val path   = Paths.get(dir)
    private val exists = Files.isDirectory(path)

    override def locate(name: String): Option[Location] = {
      if (!exists) {
        return None
      }
      if (!name.startsWith(prefix)) {
        return None
      }
      val withoutPrefix = name.stripPrefix(prefix).stripPrefix("/")
      val resolved      = path.resolve(withoutPrefix)
      if (!resolved.normalize().startsWith(path)) {
        // Escape attack
        None
      } else {
        if (Files.exists(resolved)) {
          Some(Location.File(resolved.toFile))
        } else {
          None
        }
      }
    }
  }

  /**
   * Look into Webjars using [webjarname]/webjar-resource
   *
   * @param prefix
   *   prefix which will be removed from any search file
   */
  case class Webjar(prefix: String = "") extends AssetCandidatePath {
    private var versionCache: Map[String, String] = Map.empty // scalafix:ok
    private val classLoader                       = getClass.getClassLoader

    override def locate(path: String): Option[Location] = {
      if (!path.startsWith(prefix)) {
        return None
      }
      val normalized = Paths.get(path.stripPrefix(prefix)).normalize()
      if (normalized.isAbsolute) {
        // We need a path like [component]/[artefact/path/path]
        return None
      }
      if (normalized.getNameCount <= 1) {
        // No component/sub path given
        return None
      }
      val component  = normalized.getName(0).toString
      val rest       = normalized.subpath(1, normalized.getNameCount).toString
      for {
        version <- fetchMaybeCachedVersion(component)
        fullPath = s"META-INF/resources/webjars/${component}/${version}/$rest"
        _       <- Option(classLoader.getResource(fullPath))
      } yield {
        Location.ResourcePath(fullPath, classLoader)
      }
    }

    private def fetchMaybeCachedVersion(componentName: String): Option[String] = {
      versionCache.get(componentName).orElse {
        val got = fetchVersion(componentName)
        got.foreach { version => versionCache = versionCache + (componentName -> version) }
        got
      }
    }

    private def fetchVersion(componentName: String): Option[String] = {
      try {
        val pomFile = s"META-INF/maven/org.webjars/${componentName}/pom.properties"
        Option(classLoader.getResourceAsStream(pomFile)).flatMap { stream =>
          Using.resource(stream) { _ =>
            val props = new Properties()
            props.load(stream)
            Option(props.getProperty("version"))
          }
        }
      } catch {
        case NonFatal(_) =>
          None
      }
    }
  }
}
