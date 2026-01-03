package kreuzberg.examples.showcase

import kreuzberg.{Html, SimpleComponentBase, SimpleContext}
import kreuzberg.examples.showcase.LoadingIndicator.subscribe
import kreuzberg.extras.{Router, MainRouter}
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

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
