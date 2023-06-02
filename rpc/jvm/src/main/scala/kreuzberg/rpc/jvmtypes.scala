package kreuzberg.rpc

import kreuzberg.{Provider, ServiceRepository}
import zio.Task

import scala.concurrent.Future

implicit def futureCallingBackendProvider: Provider[CallingBackend[Future, String]] =
  new Provider[CallingBackend[Future, String]] {

    override def name: String = ???

    override def create(using serviceRepository: ServiceRepository): CallingBackend[Future, String] = ???

  }

implicit def zioCallingBackendProvider: Provider[CallingBackend[Task, String]] =
  new Provider[CallingBackend[Task, String]] {

    override def name: String = ???

    override def create(using serviceRepository: ServiceRepository): CallingBackend[Task, String] = ???

  }
