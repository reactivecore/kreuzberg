package kreuzberg.rpc

import kreuzberg.{AssemblyState, Provider}
import kreuzberg.util.Stateful
import zio.Task

import scala.concurrent.Future

implicit def futureCallingBackendProvider: Provider[CallingBackend[Future, String]] = ApiRestClient.provider

implicit def zioCallingBackendProvider: Provider[CallingBackend[Task, String]] = ZioApiRestClient.provider
