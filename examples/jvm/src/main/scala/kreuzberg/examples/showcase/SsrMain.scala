package kreuzberg.examples.showcase

import kreuzberg.ServiceRepository
import kreuzberg.engine.server.{ServerEngine, ServerLocalStorageBackend}
import kreuzberg.examples.showcase.todo.{Api, TodoApi}
import kreuzberg.extras.LocalStorage
import kreuzberg.miniserver.*
import kreuzberg.rpc.{CallingBackend, Dispatcher, Id, Request, Response, Stub}

import scala.annotation.experimental
import scala.concurrent.{ExecutionContext, Future}

/** Runs the showcase with server-side rendering. */
@experimental
object SsrMain {
  def main(args: Array[String]): Unit = {
    val deploymentConfig = DeploymentConfig(
      extraHtmlHeader = defaultDeploymentConfig.extraHtmlHeader,
      deploymentType = DeploymentType.Debug
    )
    engine(MiniServerConfig(deploymentConfig, translations = Some(showcaseTranslations))).run()
  }

  /** The showcase engine. The Todo API is served via HTTP and called in-process by the server-side components. */
  def engine(config: MiniServerConfig): ServerEngine = {
    val todoDispatcher: Dispatcher[Id] = Dispatcher.makeIdDispatcher[TodoApi[Id]](new TodoServiceLoom: TodoApi[Id])

    def services(): ServiceRepository = {
      given ExecutionContext              = ExecutionContext.parasitic
      val backend: CallingBackend[Future] = new CallingBackend[Future] {
        override def call(service: String, name: String, input: Request): Future[Response] = {
          Future.successful(todoDispatcher.call(service, name, input))
        }
      }
      ServiceRepository.extensible
        .add(Api(backend, Stub.makeStub[TodoApi[Future]](backend)))
        .add(new ServerLocalStorageBackend: LocalStorage.Backend)
    }

    ServerEngine(App, () => services(), config.copy(api = Some(todoDispatcher)))
  }
}
