package kreuzberg.examples.showcase

import kreuzberg.{Html, SimpleComponentBase, SimpleContext}
import kreuzberg.examples.showcase.LoadingIndicator.subscribe
import kreuzberg.extras.SimpleRouter
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

object LoadingIndicator extends SimpleComponentBase {
  override def assemble(using c: SimpleContext): Html = {
    val loading = subscribe(SimpleRouter.loading)
    div(
      if (loading) {
        "⌛"
      } else {
        " "
      }
    )
  }
}
