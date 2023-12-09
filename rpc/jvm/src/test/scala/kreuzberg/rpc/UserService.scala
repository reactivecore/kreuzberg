package kreuzberg.rpc

import kreuzberg.rpc
import upickle.default.*

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

case class Credentials(name: String, password: String)

// Sample Objects
object Credentials {
  given writer: ReadWriter[Credentials] = macroRW

}

case class AuthenticateResult(
    token: String,
    roles: Seq[String]
)

object AuthenticateResult {
  given writer: ReadWriter[AuthenticateResult] = macroRW
}

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

class UserMock(backend: CallingBackend[Future, String])(implicit mc: MessageCodec[String], e: EffectSupport[Future])
    extends UserService[Future] {
  override def authenticate(credentials: Credentials): Future[AuthenticateResult] = {
    // Die aufrufe müssen alle irgendwie gleich aussehen.
    // Wie werden multiple argumente kodiert?
    val encoded  = mc.combine(
      "credentials" -> Codec.codec[rpc.Credentials].encode(credentials)
    )
    val response = backend.call("UserService", "authenticate", encoded)
    e.decodeResult(response)
  }

  override def logout(token: String, reason: String): Future[Boolean] = {
    val encoded  = mc.combine(
      "token"  -> Codec.codec[String].encode(token),
      "reason" -> Codec.codec[String].encode(reason)
    )
    val response = backend.call("UserService", "logout", encoded)
    e.decodeResult(response)
  }
}

class UserServiceDispatcher(backend: UserService[Future])(
    implicit mc: MessageCodec[String],
    effect: EffectSupport[Future]
) extends Dispatcher[Future, String] {
  override def handles(serviceName: String): Boolean = {
    serviceName == "UserService"
  }

  override def call(serviceName: String, name: String, input: String): Future[String] = {
    serviceName match {
      case "UserService" =>
        name match {
          case "authenticate" =>
            callAuthenticate(input)
          case "logout"       =>
            ???
          case other          =>
            effect.failure(UnknownCallError(serviceName, other))
        }
      case unknown       =>
        effect.failure(UnknownServiceError(unknown))
    }
  }

  private def callAuthenticate(input: String): Future[String] = {
    val args = for {
      decode <- mc.split(input, Seq("credentials"))
      arg0   <- Codec.codec[Credentials].decode(decode.head)
    } yield {
      arg0
    }
    effect.flatMap(effect.wrap(args)) { args =>
      effect.flatMap(backend.authenticate(args)) { result =>
        effect.success(Codec.codec[AuthenticateResult].encode(result))
      }
    }
  }
}
