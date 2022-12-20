package kreuzberg.rpc

import kreuzberg.{AssemblyState, Provider}
import kreuzberg.util.Stateful

import scala.concurrent.Future

implicit def callingBackendProvider: Provider[CallingBackend[Future, String]] = ApiRestClient.provider
