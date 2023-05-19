package kreuzberg

import kreuzberg.IdentifierFactory.instance
import kreuzberg.util.SimpleThreadLocal

import java.util.concurrent.atomic.AtomicInteger

/** Identifier for identifying things. */
opaque type Identifier = Int

object Identifier {
  def apply(id: Int): Identifier = id

  inline def next(): Identifier = IdentifierFactory.instance.next()

  extension (i: Identifier) {
    def value: Int = i
  }
}

/** Builds identifiers. */
trait IdentifierFactory {
  def next(): Identifier
}

object IdentifierFactory {
  class Default extends IdentifierFactory {
    private val current = AtomicInteger(0)

    override def next(): Identifier = Identifier(current.incrementAndGet())
  }

  private val default = new Default()

  /** Per default we share one factory for all threads. */
  private val threadLocal = new SimpleThreadLocal[IdentifierFactory](default)

  def instance: IdentifierFactory = threadLocal.get()
}
