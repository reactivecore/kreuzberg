package kreuzberg.examples.showcase

import kreuzberg.{Html, SimpleComponentBase, SimpleContext}
import kreuzberg.extras.Router
import kreuzberg.scalatags.*

object LoadingIndicator extends SimpleComponentBase {
  def assemble(using sc: SimpleContext): Html = {
    val loading = Router.loading.subscribe()
    div(
      if (loading) {
        "⌛"
      } else {
        " "
      }
    )
  }
}
