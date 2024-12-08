package kreuzberg.extras

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal
import org.scalajs.dom.window

@js.native
@JSGlobal("window")
object MainWindow extends js.Any {
  def kreuzbergInit: String = js.native
}

/** Gives access to the initialization data which can be send using initData()-Hook in MiniServer. */
object BootInitData {

  lazy val data: Option[String] = {
    Option(MainWindow.kreuzbergInit).map { base64 =>
      window.atob(base64)
    }
  }

}
