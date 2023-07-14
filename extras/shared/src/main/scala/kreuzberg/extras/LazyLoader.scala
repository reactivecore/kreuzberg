package kreuzberg.extras

import kreuzberg.{EffectOperation, EventSource, Html, Model, SimpleComponentBase, SimpleContext, SimpleHtml}

/** A Base class for components which lazy load stuff from an external service. */
abstract class LazyLoader[T] extends SimpleComponentBase {
  import LazyLoader._

  val model = Model.create[LazyState[T]](LazyState.Init)

  override def assemble(using c: SimpleContext): Html = {
    val data = subscribe(model)
    data match {
      case LazyState.Init            =>
        add(
          EventSource.Assembled
            .effect(load())
            .map {
              _.fold(err => LazyState.Failed(err), ok => LazyState.Ok(ok))
            }
            .intoModel(model),
          EventSource.Assembled.setModel(model, LazyState.WaitResponse)
        )
        waiting()
      case LazyState.WaitResponse    =>
        waiting()
      case LazyState.Ok(data)        =>
        ok(data)
      case LazyState.Failed(message) =>
        failed(message)
    }
  }

  /** Load from external service. */
  def load()(using c: SimpleContext): EffectOperation[Unit, _, T]

  /** Html which is rendered during loading. */
  def waiting()(using c: SimpleContext): Html = {
    SimpleHtml("div").addText("Loading...")
  }

  /** Html which is rendered on error. */
  def failed(error: Throwable)(using c: SimpleContext): Html = {
    SimpleHtml("div").addText(s"Error: ${error.getMessage}")
  }

  /** Html which is rendered after loading. */
  def ok(data: T)(using c: SimpleContext): Html
}

object LazyLoader {

  /** State of [[LazyLoader]] */
  sealed trait LazyState[+T]

  object LazyState {
    // Initial state
    object Init extends LazyState[Nothing]

    /** Waiting for Response */
    object WaitResponse extends LazyState[Nothing]

    /** Data available */
    case class Ok[T](data: T) extends LazyState[T]

    case class Failed(error: Throwable) extends LazyState[Nothing]
  }

}
