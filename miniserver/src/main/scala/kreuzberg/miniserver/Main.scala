package kreuzberg.miniserver

import com.typesafe.scalalogging.Logger
import zhttp.http.*
import zhttp.service.Server
import zio.*

import java.io.File
import java.nio.file.Paths

object Main extends ZIOAppDefault {
  val log = Logger(getClass)

  val mainJs = getClass.getResource("/client/fast/main.js").ensuring(_ != null)

  val clientPath = Paths.get("client/target/client_bundle/client/fast")

  val app = Http.collectHttp[Request] {
    /*
    case Method.GET -> !! / "assets" / "main.js" =>
      // Wichtig, der Slash hier muss fehlen
      Http.fromResource("client/fast/main.js")
    case Method.GET -> !! / "assets" / "main.js.map" =>
      // Wichtig, der Slash hier muss fehlen
      Http.fromResource("client/fast/main.js.map")
     */
    case Method.GET -> "" /: "assets" /: path =>
      log.info(s"Requested ${path}")
      // TODO: Escape attach
      val fullPath = clientPath.resolve(path.encode)
      log.info(s"Full path ${fullPath}")
      Http.fromFile(fullPath.toFile)
    case Method.GET -> !! =>
      log.info("Root")
      Http(
        // Response.html(Index.index.toString)
        ??? // TODO!
      )
    case Method.GET -> !! / "text" =>
      log.info("Replying...")
      Http(Response.text("Hello World"))
  }

  log.info(s"Going to listen on 8090")
  val run = Server.start(8090, app)
}
