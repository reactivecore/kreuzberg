package kreuzberg.examples.showcasezio

import kreuzberg.engine.ezio.Binder
import kreuzberg.examples.showcase.{ApiProvider, App}
import kreuzberg.extras.{BrowserLocalStorageBackend, LocalStorage}

object Main {
  def main(args: Array[String]): Unit = {
    import kreuzberg._
    Logger.enableDebug()

    given serviceRepo: ServiceRepository = ServiceRepository.extensible
      .add(ApiProvider.create())
      .add(new BrowserLocalStorageBackend: LocalStorage.Backend)

    Binder.runOnLoaded(App, "root")
  }
}
