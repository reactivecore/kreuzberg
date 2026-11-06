package kreuzberg.examples.showcase.pages

import kreuzberg.*
import kreuzberg.extras.SimpleRouted
import kreuzberg.i18n.LocalizedContent
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

/** A Markdown file per locale, rendered on the server (see `showcaseContent`). */
object AboutPage extends SimpleComponentBase with SimpleRouted {

  def assemble(using sc: SimpleContext): Html = {
    div(cls := "about", LocalizedContent("content/about").wrap)
  }

  override def path  = "/about"
  override def title = "About"
}
