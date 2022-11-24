package kreuzberg.miniserver

import com.typesafe.scalalogging.Logger
import zhttp.http.*
import zhttp.service.Server
import zio.*

import java.io.File
import java.nio.file.{Files, Paths}

class MiniServer(config: MiniServerConfig) extends ZIOAppDefault {
  val log = Logger(getClass)

  if (config.locateAsset("main.js").isEmpty) {
    println(s"Could not find client javascript code, searched in ${config.assetPaths}")
    sys.exit(1)
  }

  val indexCode = Index(config).index.toString

  val app = Http.collectHttp[Request] {
    case Method.GET -> "" /: "assets" /: path =>
      log.info(s"Requested ${path}")
      config.locateAsset(path.encode) match {
        case None                              =>
          log.warn(s"Path ${path} not found")
          Http.notFound
        case Some(Location.File(file))         =>
          Http.fromFile(file)
        case Some(Location.ResourcePath(path)) =>
          log.info(s"Serving path ${path} from resource")
          Http.fromResource(path)
      }
    case Method.GET -> !!                     =>
      log.info("Root")
      Http(
        Response.html(indexCode)
      )
    case Method.GET -> "" /: path             =>
      log.info(s"Sub path ${path}")
      Http(
        Response.html(Index(config).index.toString)
      )
  }

  log.info(s"Going to listen on ${config.port}")
  val run = Server.start(config.port, app)
}
