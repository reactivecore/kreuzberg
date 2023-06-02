package kreuzberg
import scala.collection.mutable

/** Helper for Building [[FlatHtml]] instances */
class FlatHtmlBuilder {
  private val currentPartBuilder = StringBuilder()
  private val partsBuilder       = mutable.ArrayBuilder.make[FlatHtmlElement]

  def add(s: String): Unit = {
    currentPartBuilder.append(s)
  }

  def add(cbuf: Array[Char], off: Int, len: Int): Unit = {
    currentPartBuilder.appendAll(cbuf, off, len)
  }

  /** Returns the string builder for the current part. */
  def getStringBuilder: StringBuilder = currentPartBuilder

  inline def ++=(s: String): Unit = add(s)

  def addPlaceholder(id: Identifier): Unit = {
    flush()
    partsBuilder += FlatHtmlElement.PlaceHolder(id)
  }

  private def flush(): Unit = {
    if (currentPartBuilder.nonEmpty) {
      partsBuilder += FlatHtmlElement.Part(currentPartBuilder.result())
      currentPartBuilder.clear()
    }
  }

  /** Resulting flat Html, afterwards state is not valid. */
  def result(): FlatHtml = {
    flush()
    val parts = partsBuilder.result()
    new FlatHtml(parts)
  }
}

object FlatHtmlBuilder {

  /** Current thread local FlatHtmlBuilder */
  private val currentFlattener = new SimpleThreadLocal[FlatHtmlBuilder](null)

  /**
   * Execute some block with a set flatHtmlBuilder. This can be used, to sneak a FlatHtml builder into some HTML
   * serialization method.
   */
  def withFlatHtmlBuilder[T](flatHtmlBuilder: FlatHtmlBuilder)(f: => T): T = {
    val before = currentFlattener.get()
    currentFlattener.set(flatHtmlBuilder)
    try {
      f
    } finally {
      currentFlattener.set(before)
    }
  }

  /** Returns the flat builder, if we are in processes of flattening. */
  def current: Option[FlatHtmlBuilder] = {
    Option(currentFlattener.get())
  }
}
