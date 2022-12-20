package kreuzberg.rpc

import kreuzberg.{AssemblyState, Provider}
import kreuzberg.util.Stateful

import scala.concurrent.Future

implicit def callingBackendProvider: Provider[CallingBackend[Future, String]] =
  new Provider[CallingBackend[Future, String]] {
    override def provide: Stateful[AssemblyState, CallingBackend[Future, String]] = ???
  }
