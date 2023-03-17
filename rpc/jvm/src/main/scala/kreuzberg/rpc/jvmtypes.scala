package kreuzberg.rpc

import kreuzberg.{AssemblyState, Provider}
import kreuzberg.util.Stateful
import zio.Task

import scala.concurrent.Future

implicit def futureCallingBackendProvider: Provider[CallingBackend[Future, String]] =
  new Provider[CallingBackend[Future, String]] {
    override def provide: Stateful[AssemblyState, CallingBackend[Future, String]] = ???
  }

implicit def zioCallingBackendProvider: Provider[CallingBackend[Task, String]] =
  new Provider[CallingBackend[Task, String]] {
    override def provide: Stateful[AssemblyState, CallingBackend[Task, String]] = ???
  }
