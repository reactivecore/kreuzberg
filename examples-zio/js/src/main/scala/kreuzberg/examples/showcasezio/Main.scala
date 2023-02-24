package kreuzberg.examples.showcasezio

import kreuzberg.engine.ezio.Binder
import kreuzberg.examples.showcase.App

object Main {
  def main(args: Array[String]): Unit = {
    import kreuzberg._
    val app = App()
    Logger.enableDebug()
    Binder.runOnLoaded(app, "root")
  }
}
