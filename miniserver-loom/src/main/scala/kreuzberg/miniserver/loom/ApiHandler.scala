package kreuzberg.miniserver.loom

import io.circe.Json
import kreuzberg.rpc.{Dispatcher, Id, UnknownServiceError}
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.json.circe.*

import scala.util.control.NonFatal
import quest._

case class ApiHandler(dispatcher: Dispatcher[Id]) {

  val apiEndpoint
      : PublicEndpoint[(List[String], Json, List[sttp.model.Header]), (Json, StatusCode), (Json, StatusCode), Any] = {
    endpoint.post
      .in("api" / paths)
      .in(jsonBody[Json])
      .in(headers)
      .out(jsonBody[Json])
      .out(statusCode)
      .errorOut(jsonBody[Json])
      .errorOut(statusCode)
  }

  val handler = apiEndpoint.serverLogic[Id] { case (paths, json, headers) =>
    quest[Either[(Json, StatusCode), (Json, StatusCode)]] {
      val (serviceName, callName) = paths match {
        case List(s, c) => (s, c)
        case _          => bail(Left(Json.obj("msg" -> Json.fromString("Invalid path")) -> StatusCode.BadRequest))
      }

      if (!dispatcher.handles(serviceName)) {
        bail(Left(UnknownServiceError(serviceName).encodeToJson -> StatusCode.NotFound))
      }

      val request  = kreuzberg.rpc.Request(json, headers.map { h => h.name -> h.value })
      val response =
        try {
          dispatcher.call(serviceName, callName, request)
        } catch {
          case NonFatal(e) =>
            val decoded = kreuzberg.rpc.Failure.fromThrowable(e)
            bail(Left(decoded.encodeToJson -> StatusCode.InternalServerError))
        }
      Right(response.json -> StatusCode(response.statusCode))
    }
  }
}
