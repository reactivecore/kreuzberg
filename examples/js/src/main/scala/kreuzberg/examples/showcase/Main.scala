package kreuzberg.examples.showcase

import kreuzberg.engine.naive.Binder

object Main {
  def main(args: Array[String]): Unit = {
    import kreuzberg._
    Logger.enableDebug()
    Logger.enableTrace()
    Binder.runOnLoaded(App, "root")
  }
}
