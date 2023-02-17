package kreuzberg

import kreuzberg.dom.ScalaJsElement

/** Something which can extract java script states from Components. */
trait StateGetter[S] {
  def get(element: ScalaJsElement): S
}

object StateGetter {

  case class JsRepresentationState[J <: ScalaJsElement, S](f: J => S) extends StateGetter[S] {
    override def get(element: ScalaJsElement): S = {
      val casted  = element.asInstanceOf[J]
      f(casted)
    }
  }

}
