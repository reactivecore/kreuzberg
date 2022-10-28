package kreuzberg.miniserver
import scalatags.Text.all._

object Index {
  val index = html(
    head(
      script(src := "/assets/main.js")
    ),
    body(
      h1("Hello World"),
      div(id := "root")
    )
  )
}
