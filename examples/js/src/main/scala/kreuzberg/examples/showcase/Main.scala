package kreuzberg.examples.showcase

import kreuzberg.engine.naive.Binder
import kreuzberg.extras.{BrowserLocalStorageBackend, LocalStorage}

import scala.annotation.experimental

@experimental
object Main {
  def main(args: Array[String]): Unit = {
    import kreuzberg._
    Logger.enableDebug()
    Logger.enableTrace()

    given serviceRepo: ServiceRepository = ServiceRepository.extensible
      .add(ApiProvider.create())
      .add(new BrowserLocalStorageBackend: LocalStorage.Backend)

    Binder.runOnLoaded(App, "root")
  }
}
