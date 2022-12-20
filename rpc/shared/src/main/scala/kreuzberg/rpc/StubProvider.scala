package kreuzberg.rpc

import kreuzberg.Provider
import kreuzberg.AssemblyState
import kreuzberg.util.Stateful
import scala.concurrent.Future

/** Provides stubs for API Clients. */
object StubProvider {

  /** Provides a stub for given Client. */
  inline def stubProvider[T](using restClientProvider: Provider[CallingBackend[Future, String]]): Provider[T] =
    new Provider[T] {
      def provide: Stateful[AssemblyState, T] = {
        Stateful { (s0: AssemblyState) =>
          val serviceName      = "stub_" + Stub.serviceName[T]
          val (s1, restClient) = restClientProvider.provide(s0)
          val (s2, service)    = s1.rootService(
            serviceName,
            () => {
              Stub.makeStub[T](restClient)
            }
          )
          (s2, service)
        }
      }
    }
}
