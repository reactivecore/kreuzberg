package kreuzberg.miniserver

import kreuzberg.rpc.Dispatcher
import zio.*
import zio.http.*
import zio.logging.backend.SLF4J

import java.nio.file.Paths

class MiniServer(config: MiniServerConfig) extends ZIOAppDefault {
  /** Hook before startup. */
  def preflightCheck: Task[Unit] = {
    ZIO
      .fromOption(config.locateAsset("main.js"))
      .mapError { _ =>
        val cwd = Paths.get("").toAbsolutePath
        new IllegalStateException(
          s"Could not find client javascript code, searched in ${config.assetPaths}, working directory=$cwd"
        )
      }
      .unit
  }

  val indexHtml: String = Index(config).index.toString

  val assetProvider: HttpApp[Any, Throwable] = Http.collectHttp[Request] {
    case Method.GET -> "" /: "assets" /: path =>
      config.locateAsset(path.encode) match {
        case None                              =>
          Http.fromHandler(Handler.notFound)
        case Some(Location.File(file))         =>
          Http.fromFile(file)
        case Some(Location.ResourcePath(path)) =>
          Http.fromResource(path)
      }
    case Method.GET -> Root                   =>
      Http.fromHandler(
        Handler.html(
          indexHtml
        )
      )
    case Method.GET -> "" /: path             =>
      Http.fromHandler(
        Handler.html(
          Index(config).index.toString
        )
      )
  } @@ HttpAppMiddleware.requestLogging()

  val configLayer = Server.defaultWithPort(config.port)

  val myApp = for {
    _            <- preflightCheck
    _            <- ZIO.logInfo(s"Going to listen on ${config.port}")
    apiEffect     = config.api.getOrElse(ZIO.succeed(Dispatcher.empty: ZioDispatcher))
    dispatcher   <- apiEffect
    apiDispatcher = ApiDispatcher(dispatcher)
    extraAppTask  = config.extraApp.getOrElse(ZIO.succeed(Http.collectHttp[Request].apply(PartialFunction.empty)))
    extraApp     <- extraAppTask
    all           = (apiDispatcher.app() ++ extraApp ++ assetProvider).withDefaultErrorResponse
    port         <- Server.install(all)
    _            <- ZIO.logInfo(s"Started server on port: ${port}")
    _            <- ZIO.never
  } yield {
    ()
  }

  val switchToSlf4j = Runtime.removeDefaultLoggers >>> SLF4J.slf4j

  val run = myApp.provide(switchToSlf4j, configLayer)
}
