package kreuzberg.rpc

import kreuzberg.{Provider, ServiceRepository}

import scala.concurrent.Future

/** Provides stubs for API Clients. */
object StubProvider {

  /** Provides a stub for given Client. */
  inline given stubProvider[T](using restClientProvider: Provider[CallingBackend[Future, String]]): Provider[T] =
    new Provider[T] {

      override def name: String = "stub_" + Stub.serviceName[T]

      override def create(using serviceRepository: ServiceRepository): T = {
        val restClient = serviceRepository.service[CallingBackend[Future, String]]
        Stub.makeStub[T](restClient)
      }
    }
}
