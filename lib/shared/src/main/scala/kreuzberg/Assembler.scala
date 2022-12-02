package kreuzberg

import kreuzberg.util.Stateful

import scala.language.implicitConversions

object AssemblyResult {
  implicit def fromHtml(html: Html): AssemblyResult = {
    Stateful.pure(Assembly(html))
  }
}

/** A type class which is needed to assemble things */
trait Assembler[T] {
  self =>

  /** Assembles the object. IDs already set. */
  def assemble(value: T): AssemblyResult

  /** Applies f before applying Assembler */
  def contraMap[U](f: U => T): Assembler[U] = { value =>
    self.assemble(f(value))
  }

  /** Maps the HTML Code. */
  def mapHtml(f: Html => Html): Assembler[T] = { value =>
    self.assemble(value).map(_.map(f))
  }

  /** Converts it into a assembler for a sequence */
  def seq(around: Html): Assembler[Seq[T]] = {
    Assembler.seq(around)(this)
  }

  def assembleWithId(id: ComponentId, value: T): NodeResult[T] = {
    for {
      _         <- Stateful.modify[AssemblyState](_.pushId(id))
      assembled <- assemble(value)
      _         <- Stateful.modify[AssemblyState](_.popId)
    } yield ComponentNode(id, value, assembled, this)
  }

  /** Assemble the object as anonymous child. */
  def assembleWithNewId(value: T): NodeResult[T] = {
    for {
      id        <- Stateful[AssemblyState, ComponentId](_.generateId)
      assembled <- assembleWithId(id, value)
    } yield {
      assembled
    }
  }

  def assembleNamedChild(name: String, value: T): NodeResult[T] = {
    for {
      id        <- Stateful[AssemblyState, ComponentId](_.ensureChildren(name))
      assembled <- assembleWithId(id, value)
    } yield {
      assembled
    }
  }
}

object Assembler {
  def plain[T](f: T => Html): Assembler[T] = (value: T) => {
    Stateful.pure(
      Assembly(f(value))
    )
  }

  def apply[T](implicit a: Assembler[T]): Assembler[T] = {
    a
  }

  /**
   * Assemble a value as a single component discarding the state. For testcases.
   */
  def single[T](value: T)(using a: Assembler[T]): Assembly = {
    a.assemble(value)(AssemblyState())._2
  }

  /** Concatenates some html */
  def seq[T](around: Html)(implicit a: Assembler[T]): Assembler[Seq[T]] = { values =>
    for {
      children <- Stateful.accumulate(values)(value => a.assembleWithNewId(value))
    } yield {
      Assembly.Container(
        children,
        renderer = htmls => around.addInner(htmls)
      )
    }
  }
}
