package kreuzberg.extras

object MetaUtil {

  private val metaAttribute = "data-managed"
  private val metaValue     = "kreuzberg"

  private def clearMetaElements(): Unit = {
    val metaElements = org.scalajs.dom.document.head.getElementsByTagName("meta")

    metaElements
      .filter(_.getAttribute(metaAttribute) == metaValue)
      .reverse
      .foreach(_.remove())
  }

  def injectMetaData(meta: MetaData): Unit = {
    clearMetaElements()

    if (meta.isEmpty) {
      return
    }

    meta.foreach { x =>
      val elem = org.scalajs.dom.document.createElement("meta").asInstanceOf[org.scalajs.dom.html.Meta]

      elem.setAttribute(x.fieldName, x.fieldValue)
      x.content.foreach { content =>
        elem.content = content
      }

      elem.setAttribute(metaAttribute, metaValue)
      org.scalajs.dom.document.head.append(elem)
    }
  }
}
