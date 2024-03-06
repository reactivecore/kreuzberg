package kreuzberg.miniserver

import kreuzberg.rpc.Dispatcher
import zio.{Cause, IO, Task, UIO, ZIO}
import kreuzberg.rpc.Failure
import zio.http.*
import kreuzberg.rpc.CodecError
import kreuzberg.rpc.ServiceExecutionError

import scala.util.control.NonFatal

type ZioDispatcher = Dispatcher[Task]

case class ApiDispatcher(backend: ZioDispatcher) {

  def app(): HttpApp[Any, Throwable] = Http.collectZIO[Request] {
    case Method.GET -> "" /: "api" /: path                        =>
      ZIO.succeed(Response.text("API Requests requires POST").withStatus(Status.MethodNotAllowed))
    case r @ Method.POST -> Root / "api" / serviceName / callName =>
      encodeErrors(serviceName, callName) {
        for {
          body     <- r.body.asString
          request  <- ZIO.attempt(
                        kreuzberg.rpc.Request.forceJsonString(
                          body,
                          r.headers.map { header =>
                            header.headerName -> header.renderedValue
                          }.toList
                        )
                      )
          response <- {
            // Also attempt call, otherwise we get hard to debug HTTP 500 errors
            // When the backend.call itself throws an exception.
            ZIO.attempt {
              backend.call(serviceName, callName, request)
            }.flatten
          }
        } yield {
          Response
            .json(response.json.toString)
            .withStatus(Status.fromInt(response.statusCode).getOrElse(Status.Ok))
        }
      }
    case Method.POST -> "" /: "api" /: path                       =>
      ZIO.succeed(Response.text("Invalid API Request").withStatus(Status.BadRequest))
  }

  def encodeErrors(serviceName: String, callName: String)(in: Task[Response]): UIO[Response] = {
    in.catchAll {
      case f: Failure  =>
        for {
          _ <- ZIO.logInfoCause(s"Failed request ${serviceName}/${callName}", Cause.fail(f))
        } yield {
          Response.json(f.encode.toJson.toString).withStatus(Status.BadRequest)
        }
      case NonFatal(e) =>
        for {
          _ <- ZIO.logWarningCause(s"Failed Request ${serviceName}/${callName}", Cause.fail(e))
        } yield {
          // Do not add internal information, this may leak sensitive information to the user
          val wrapped = ServiceExecutionError("Internal Error")
          Response.json(wrapped.encode.toJson.toString).withStatus(Status.InternalServerError)
        }
      case e           =>
        for {
          _ <- ZIO.logErrorCause(s"Fatal failed request ${serviceName}/${callName}", Cause.fail(e))
        } yield {
          // Do not add internal information, this may leak sensitive information to the user
          val wrapped = ServiceExecutionError("Fatal Error")
          Response.json(wrapped.encode.toJson.toString).withStatus(Status.InternalServerError)
        }
    }

  }
}
