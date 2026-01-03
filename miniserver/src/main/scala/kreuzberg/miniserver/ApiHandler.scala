package kreuzberg.miniserver

import io.circe.Json
import kreuzberg.rpc.{Dispatcher, Id, UnknownCallError, UnknownServiceError}
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.json.circe.*

import scala.util.control.NonFatal
import quest.*
import sttp.model.headers.CookieWithMeta

case class ApiHandler(dispatcher: Dispatcher[Id]) {

  val apiEndpoint: PublicEndpoint[
    (List[String], Json, List[sttp.model.Header], List[sttp.model.headers.Cookie]),
    (Json, StatusCode),
    (Json, StatusCode, List[CookieWithMeta]),
    Any
  ] = {
    endpoint.post
      .in("api" / paths)
      .in(jsonBody[Json])
      .in(headers)
      .in(cookies)
      .out(jsonBody[Json])
      .out(statusCode)
      .out(setCookies)
      .errorOut(jsonBody[Json])
      .errorOut(statusCode)
  }

  val handler = apiEndpoint.serverLogic[Id] { case (paths, json, headers, cookies) =>
    quest[Either[(Json, StatusCode), (Json, StatusCode, List[CookieWithMeta])]] {
      val (serviceName, callName) = paths match {
        case List(s, c) => (s, c)
        case _          => bail(Left(Json.obj("msg" -> Json.fromString("Invalid path")) -> StatusCode.BadRequest))
      }

      if (!dispatcher.handles(serviceName)) {
        bail(Left(UnknownServiceError(serviceName).encodeToJson -> StatusCode.NotFound))
      }
      val jsonObject = json.asObject.getOrElse {
        bail(Left(Json.obj("msg" -> Json.fromString("Expected JSON object")) -> StatusCode.BadRequest))
      }

      val request           = kreuzberg.rpc.Request(
        jsonObject,
        headers.map { h => h.name -> h.value },
        cookies.map { c => c.name -> c.value }
      )
      val response          =
        try {
          dispatcher.call(serviceName, callName, request)
        } catch {
          case error: UnknownCallError =>
            bail(Left(error.encodeToJson -> StatusCode.NotFound))
          case NonFatal(e)             =>
            val decoded = kreuzberg.rpc.Failure.fromThrowable(e)
            bail(Left(decoded.encodeToJson -> StatusCode.InternalServerError))
        }
      val translatedCookies = response.setCookies.map { s =>
        CookieWithMeta.unsafeParse(s)
      }
      Right(
        (response.json, StatusCode(response.statusCode), translatedCookies)
      )
    }
  }
}
