package kreuzberg.miniserver

import com.typesafe.scalalogging.Logger
import zhttp.http.*
import zhttp.service.Server
import zio.*

import java.io.File
import java.nio.file.{Files, Paths}

object MiniServer extends ZIOAppDefault {
  val log = Logger(getClass)

  val candidatePaths = Seq(
    "examples/target/client_bundle/client/fast",
    "../examples/target/client_bundle/client/fast"
  )

  val clientPath = candidatePaths.find(s => Files.isDirectory(Paths.get(s))).getOrElse {
    println(s"Could not find client javascript code, searched in ${candidatePaths}")
    sys.exit(1)
  }

  val nioPath = Paths.get(clientPath)
  val app     = Http.collectHttp[Request] {
    case Method.GET -> "" /: "assets" /: path =>
      log.info(s"Requested ${path}")
      // TODO: Escape attack
      val fullPath = nioPath.resolve(path.encode)
      log.info(s"Full path ${fullPath}")
      Http.fromFile(fullPath.toFile)
    case Method.GET -> !!                     =>
      log.info("Root")
      Http(
        Response.html(Index.index.toString)
      )
    case Method.GET -> "" /: path             =>
      log.info(s"Sub path ${path}")
      Http(
        Response.html(Index.index.toString)
      )
  }

  log.info(s"Going to listen on 8090")
  val run = Server.start(8090, app)
}
