package kreuzberg.imperative

import kreuzberg.*
import kreuzberg.util.Stateful

import kreuzberg.dom.ScalaJsElement

/**
 * Base class for components, which assemble imperative. Also see [[SimpleComponentBase]] for an even reduced version.
 */
abstract class ImperativeComponentBase extends ImperativeDsl with Component {

  type Runtime = Unit

  def assemble(implicit c: AssemblyContext): Assembly[Unit]

  final def assemble: AssemblyResult[Runtime] = {
    AssemblyContext.transform { context =>
      assemble(context)
    }
  }
}

/** Imperative variant if [[AssemblyState]] */
class AssemblyContext(private var _state: AssemblyState) {
  def transform[T](f: Stateful[AssemblyState, T]): T = {
    val (nextState, value) = f(_state)
    _state = nextState
    value
  }

  def transformFn[T](f: AssemblyState => (AssemblyState, T)): T = {
    val (nextState, value) = f(_state)
    _state = nextState
    value
  }

  def get[T](f: AssemblyState => T): T = {
    f(_state)
  }

  def state: AssemblyState = _state
}

object AssemblyContext {

  /** Transform an imperative function using assembly context back into a stateful instance. */
  def transform[T](f: AssemblyContext => T): Stateful[AssemblyState, T] = {
    Stateful.apply { state =>
      val context = new AssemblyContext(state)
      val result  = f(context)
      (context.state, result)
    }
  }
}

trait ImperativeDsl {
  protected def child[R, T <: Component.Aux[R]](
      name: String,
      component: T
  )(implicit c: AssemblyContext): ComponentNode[R, T] = {
    c.transform(Assembler.assembleNamedChild(name, component))
  }

  protected def anonymousChild[R, T <: Component.Aux[R]](
      component: T
  )(implicit c: AssemblyContext): ComponentNode[R, T] = {
    c.transform(Assembler.assembleWithNewId(component))
  }

  protected def subscribe[M](model: Model[M])(implicit c: AssemblyContext): M = {
    c.transformFn(_.subscribe(model))
  }

  protected def read[M](model: Model[M])(implicit c: AssemblyContext): M = {
    c.get(_.readValue(model))
  }

  protected def provide[T: Provider](implicit c: AssemblyContext): T = {
    c.transformFn(_.provide[T])
  }

  case class RepBuilder[T <: Component](rep: TreeNodeC[T]) {
    def apply[E](f: T => Event[E]): EventSource[E] = EventSource.ComponentEvent(f(rep.component), Some(rep.id))
  }

  /** Helper for selecting events from children. */
  def from[T <: Component](rep: TreeNodeC[T]): RepBuilder[T] = RepBuilder(rep)

  /** Helper for selecting own events. */
  def own[E](event: Event[E]): EventSource[E] = EventSource.ComponentEvent(event)

  import scala.language.implicitConversions
  implicit def htmlToAssembly(in: Html): Assembly[Unit] = {
    Assembly(in)
  }

  def html[T](in: T)(implicit f: T => Html): Html = f(in)

  extension (html: Html) {
    def withRuntime[T](f: RuntimeContext ?=> T): (Html, RuntimeProvider[T]) = {
      html -> { rc =>
        f(using rc)
      }
    }
  }

  extension [R](node: TreeNodeR[R]) {

    /** Accessor for runtime node. */
    def state(implicit runtimeContext: RuntimeContext): R = {
      val subContext = runtimeContext.jump(node.id)
      node.runtimeProvider(subContext)
    }
  }
}
