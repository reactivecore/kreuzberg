package kreuzberg

import org.scalajs.dom.Element

/** Something which can extract java script states from Components. */
trait StateGetter[S] {
  def get(id: ComponentId, viewer: Viewer): S
}

object StateGetter {

  case class JsRepresentationState[J <: Element, S](f: J => S) extends StateGetter[S] {
    override def get(id: ComponentId, viewer: Viewer): S = {
      val element = viewer.findElement(id)
      val casted  = element.asInstanceOf[J]
      f(casted)
    }
  }

}
