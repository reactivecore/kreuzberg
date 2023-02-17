package kreuzberg.miniserver

import com.typesafe.scalalogging.Logger
import kreuzberg.rpc.Dispatcher
import zio.{IO, Task, UIO, ZIO}
import kreuzberg.rpc.Failure
import zio.http.*
import zio.http.model.*
import kreuzberg.rpc.CodecError
import kreuzberg.rpc.ServiceExecutionError

import scala.util.control.NonFatal

type ZioDispatcher = Dispatcher[Task, String]

case class ApiDispatcher(backend: ZioDispatcher) {

  private lazy val logger = Logger(getClass)

  def app(): HttpApp[Any, Nothing] = Http.collectZIO[Request] {
    case Method.GET -> "" /: "api" /: path                      =>
      ZIO.succeed(Response.text("API Requests requires POST").setStatus(Status.MethodNotAllowed))
    case r @ Method.POST -> !! / "api" / serviceName / callName =>
      encodeErrors(serviceName, callName) {
        for {
          body     <- r.body.asString
          response <- backend.call(serviceName, callName, body)
        } yield {
          Response.json(response)
        }
      }
    case Method.POST -> "" /: "api" /: path                     =>
      ZIO.succeed(Response.text("Invalid API Request").setStatus(Status.BadRequest))
  }

  def encodeErrors(serviceName: String, callName: String)(in: Task[Response]): UIO[Response] = {
    in.catchAll {
      case f: Failure  =>
        logger.info(s"Failed request ${serviceName}/${callName}", f)
        ZIO.succeed(Response.json(f.encode.toJson).setStatus(Status.BadRequest))
      case NonFatal(e) =>
        logger.warn(s"Failed Request ${serviceName}/${callName}", e)
        // Do not add internal information, this may leak sensitive information to the user
        val wrapped = ServiceExecutionError("Internal Error")
        ZIO.succeed(
          Response.json(wrapped.encode.toJson).setStatus(Status.InternalServerError)
        )
      case e           =>
        logger.error(s"Fatal failed request ${serviceName}/${callName}", e)
        // Do not add internal information, this may leak sensitive information to the user
        val wrapped = ServiceExecutionError("Fatal Error")
        ZIO.succeed(
          Response.json(wrapped.encode.toJson).setStatus(Status.InternalServerError)
        )
    }

  }
}
