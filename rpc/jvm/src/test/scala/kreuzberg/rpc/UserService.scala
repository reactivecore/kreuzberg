package kreuzberg.rpc

import io.circe.syntax.*
import io.circe.{Codec, Encoder}
import kreuzberg.rpc

import scala.concurrent.Future

case class Credentials(name: String, password: String) derives Codec.AsObject

case class AuthenticateResult(
    token: String,
    roles: Seq[String]
) derives Codec.AsObject

// Sample Service
trait UserService[F[_]] {

  def authenticate(credentials: Credentials): F[AuthenticateResult]

  def logout(token: String, reason: String): F[Boolean]
}

// Sample Server Implementation
object UserServiceDummy extends UserService[Future] {
  override def authenticate(credentials: Credentials): Future[AuthenticateResult] = {
    Future.successful(
      AuthenticateResult("123", Seq("admin"))
    )
  }

  override def logout(token: String, reason: String): Future[Boolean] = {
    Future.successful(
      true
    )
  }
}

class UserMock(backend: CallingBackend[Future])(implicit e: EffectSupport[Future]) extends UserService[Future] {
  override def authenticate(credentials: Credentials): Future[AuthenticateResult] = {
    // Die aufrufe müssen alle irgendwie gleich aussehen.
    // Wie werden multiple argumente kodiert?
    val encoded  = MessageCodec.combine(
      "credentials" -> credentials.asJson
    )
    val response = backend.call("UserService", "authenticate", Request(encoded))
    e.decodeResponse(response)
  }

  override def logout(token: String, reason: String): Future[Boolean] = {
    val encoded  = MessageCodec.combine(
      "token"  -> token.asJson,
      "reason" -> reason.asJson
    )
    val response = backend.call("UserService", "logout", Request(encoded))
    e.decodeResponse(response)
  }
}

class UserServiceDispatcher(backend: UserService[Future])(
    implicit effect: EffectSupport[Future]
) extends Dispatcher[Future] {
  override def handles(serviceName: String): Boolean = {
    serviceName == "UserService"
  }

  override def call(serviceName: String, name: String, request: Request): Future[Response] = {
    serviceName match {
      case "UserService" =>
        name match {
          case "authenticate" =>
            callAuthenticate(request)
          case "logout"       =>
            ???
          case other          =>
            effect.failure(UnknownCallError(serviceName, other))
        }
      case unknown       =>
        effect.failure(UnknownServiceError(unknown))
    }
  }

  private def callAuthenticate(input: Request): Future[Response] = {
    val args         = for {
      decode <- MessageCodec.split(input.payload, Seq("credentials"))
      arg0   <- decode.head.as[Credentials]
    } yield {
      arg0
    }
    effect.flatMap(effect.wrap(args.left.map(Failure.fromCirceError))) { args =>
      effect.flatMap(backend.authenticate(args)) { result =>
        effect.success(Response(result.asJson))
      }
    }
  }
}
