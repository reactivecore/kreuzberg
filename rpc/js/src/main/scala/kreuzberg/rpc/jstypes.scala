package kreuzberg.rpc

import kreuzberg.Provider
import zio.Task

import scala.concurrent.Future

implicit def futureCallingBackendProvider: Provider[CallingBackend[Future, String]] = ApiRestClient.provider

implicit def zioCallingBackendProvider: Provider[CallingBackend[Task, String]] = ZioApiRestClient.provider
