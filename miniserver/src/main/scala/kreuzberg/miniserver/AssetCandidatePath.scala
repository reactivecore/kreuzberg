package kreuzberg.miniserver

import java.io.{File, FileInputStream, IOException, InputStream, OutputStream}
import java.net.JarURLConnection
import java.nio.file.{Files, Paths}
import java.security.{DigestInputStream, MessageDigest}
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import scala.language.implicitConversions
import scala.util.Using
import scala.util.control.NonFatal

sealed trait Location {

  /** Load the ressource. */
  def load(): InputStream

  /** Size in bytes. */
  def size(): Long

  /** The Brotli compressed variant (`.br`), if there is one. */
  def brotliVariant(): Option[Location]

  /** Changes when the content changes. */
  protected def contentVersion(): (Long, Long)

  /**
   * SHA-256 of the content (hex). In production, it is cached until the modification time or size changes. Not cached
   * in debug mode, where files may be rebuilt faster than the modification time changes.
   */
  def hashSha256(deploymentType: Option[DeploymentType]): String = {
    if (deploymentType.contains(DeploymentType.Production)) {
      cachedHashSha256()
    } else {
      hashSha256()
    }
  }

  /** SHA-256 of the content (hex). */
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

  private def cachedHashSha256(): String = {
    val version = contentVersion()
    Option(Location.hashCache.get(this))
      .collect { case (`version`, hash) => hash }
      .getOrElse {
        val hash = hashSha256()
        Location.hashCache.put(this, (version, hash))
        hash
      }
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

  /** Hashes by location, with the content version they were computed for. */
  private val hashCache = new ConcurrentHashMap[Location, ((Long, Long), String)]()

  case class File(file: java.io.File) extends Location {
    override def load(): InputStream = {
      new FileInputStream(file)
    }

    override def size(): Long = file.length()

    override def brotliVariant(): Option[Location] = {
      Some(File(new java.io.File(file.getPath + ".br"))).filter(_.file.exists())
    }

    /** Files may change while running (e.g. rebuilt in debug mode). */
    override protected def contentVersion(): (Long, Long) = (file.lastModified(), file.length())
  }

  case class ResourcePath(path: String, classLoader: ClassLoader) extends Location {
    override def load(): InputStream = {
      val stream = classLoader.getResourceAsStream(path)
      if (stream == null) {
        throw new IOException(s"Could not load resource ${path}")
      }
      stream
    }

    override def size(): Long = {
      val url        = Option(classLoader.getResource(path)).getOrElse {
        throw new IOException(s"Could not find resource ${path}")
      }
      val fromSource = url.openConnection() match {
        case jar: JarURLConnection          => Some(jar.getJarEntry.getSize).filter(_ >= 0)
        case _ if url.getProtocol == "file" => Some(new java.io.File(url.toURI).length())
        case _                              => None
      }
      fromSource.getOrElse {
        Using.resource(load())(_.transferTo(OutputStream.nullOutputStream()))
      }
    }

    override def brotliVariant(): Option[Location] = {
      val brotliPath = path + ".br"
      Option(classLoader.getResource(brotliPath)).map(_ => ResourcePath(brotliPath, classLoader))
    }

    /** Resources in JARs do not change while running, resources in directories (e.g. target/classes) may. */
    override protected def contentVersion(): (Long, Long) = {
      Option(classLoader.getResource(path)).filter(_.getProtocol == "file") match {
        case Some(url) =>
          val file = new java.io.File(url.toURI)
          (file.lastModified(), file.length())
        case None      => (0L, 0L)
      }
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
        if (Files.isRegularFile(resolved)) {
          // Normalized, so that other spellings of the path share hash cache entries
          Some(Location.File(resolved.normalize().toFile))
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
   * @param groups
   *   maven groups which are searched for the webjar's version descriptor, in order. The defaults cover classical
   *   (`org.webjars`) and npm-derived (`org.webjars.npm`) webjars, add e.g. `org.webjars.bower` if needed.
   */
  case class Webjar(prefix: String = "", groups: Seq[String] = Webjar.DefaultGroups) extends AssetCandidatePath {
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
      groups.view.flatMap { group =>
        readVersion(s"META-INF/maven/${group}/${componentName}/pom.properties")
      }.headOption
    }

    private def readVersion(pomFile: String): Option[String] = {
      try {
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

  object Webjar {

    /** Default maven groups which are searched for webjar version descriptors. */
    val DefaultGroups: Seq[String] = Seq("org.webjars", "org.webjars.npm")
  }
}
