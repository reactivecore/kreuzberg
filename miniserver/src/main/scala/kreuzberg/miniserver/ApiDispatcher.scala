package kreuzberg.miniserver

import kreuzberg.rpc.Dispatcher
import zio.{ZIO, IO, Task, UIO}
import kreuzberg.rpc.Failure
import zhttp.http.*
import kreuzberg.rpc.CodecError
import kreuzberg.rpc.ServiceExecutionError
import scala.util.control.NonFatal

type ZioDispatcher = Dispatcher[Task, String]

case class ApiDispatcher(backend: ZioDispatcher) {
  def app(): HttpApp[Any, Nothing] = Http.collectZIO[Request] {
    case Method.GET -> "" /: "api" /: path                      =>
      ZIO.succeed(Response.text("API Requests requires POST").setStatus(Status.MethodNotAllowed))
    case r @ Method.POST -> !! / "api" / serviceName / callName =>
      encodeErrors {
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

  def encodeErrors(in: Task[Response]): UIO[Response] = {
    in.catchAll {
      case f: Failure  => ZIO.succeed(Response.json(f.encode.toJson).setStatus(Status.BadRequest))
      case NonFatal(e) =>
        val wrapped = ServiceExecutionError(e.getMessage)
        ZIO.succeed(
          Response.json(wrapped.encode.toJson).setStatus(Status.InternalServerError)
        )
    }

  }
}
