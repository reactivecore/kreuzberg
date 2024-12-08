package kreuzberg.examples.showcase

import kreuzberg.engine.naive.Binder
import kreuzberg.extras.{BootInitData, BrowserLocalStorageBackend, LocalStorage}

import scala.annotation.experimental

@experimental
object Main {

  lazy val parsedInit: Option[InitData] = for {
    data      <- BootInitData.data
    parsed    <- io.circe.parser.parse(data).toOption
    converted <- parsed.as[InitData].toOption
  } yield converted

  def main(args: Array[String]): Unit = {
    import kreuzberg._
    Logger.enableDebug()
    Logger.enableTrace()

    Logger.debug(s"Init code: ${parsedInit}")

    given serviceRepo: ServiceRepository = ServiceRepository.extensible
      .add(ApiProvider.create())
      .add(new BrowserLocalStorageBackend: LocalStorage.Backend)

    Binder.runOnLoaded(App, "root")
  }
}
