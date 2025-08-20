package kreuzberg

import scala.concurrent.ExecutionContext

/**
 * A Headless is like a component, but without an HTML-Representation. However it can define EventHandlers and
 * Subscriptions.
 */
trait HeadlessComponent extends Identified {
  final val id: Identifier = Identifier.next()

  protected implicit def ec: ExecutionContext = KreuzbergContext.get().ec

  def assemble: HeadlessAssembly
}

/** Assembled service. */
case class HeadlessAssembly(
    handlers: Vector[EventBinding[?]] = Vector.empty,
    subscriptions: Vector[Subscribeable[?]] = Vector.empty,
    children: Vector[HeadlessComponent] = Vector.empty
)

/** Base trait for services. */
trait HeadlessComponentBase extends HeadlessComponent with ContextDsl
