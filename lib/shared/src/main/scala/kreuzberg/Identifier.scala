package kreuzberg

import java.util.concurrent.atomic.AtomicInteger

/** Some object which has an identifier. */
trait Identified {

  /** Id of this thing. */
  def id: Identifier

  /** Comment, which will probably built into into a tree. Can be disabled by returning "" */
  def comment: String = getClass.getSimpleName.stripSuffix("$")
}

/** Identifier for identifying things. */
opaque type Identifier = Int

object Identifier {
  def apply(id: Int): Identifier = id

  inline def next(): Identifier = IdentifierFactory.instance.next()

  extension (i: Identifier) {
    def value: Int = i
  }

  /** Anonymous root components, owner of root models. */
  val RootComponent = Identifier(0)
}

/** Builds identifiers. */
trait IdentifierFactory {
  def next(): Identifier
}

object IdentifierFactory {
  class Default extends IdentifierFactory {
    // 0 is reserved for Root Identifier!
    private val _nextValue = AtomicInteger(1)

    override def next(): Identifier = Identifier(_nextValue.getAndIncrement())
  }

  private val default = new Default()

  /** Per default we share one factory for all threads. */
  private val threadLocal = new SimpleThreadLocal[IdentifierFactory](default)

  def instance: IdentifierFactory = threadLocal.get()

  /** Execute some f with a fresh factory (e.g. testcases with semi-reliable ids) */
  def withFresh[T](f: => T): T = {
    using(new Default())(f)
  }

  /** Execute some block f with a specific factory. */
  def using[T](factory: IdentifierFactory)(f: => T): T = {
    val current = threadLocal.get()
    threadLocal.set(factory)
    try {
      f
    } finally {
      threadLocal.set(current)
    }
  }
}
