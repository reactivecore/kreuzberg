package kreuzberg.extras

import kreuzberg.*

/** Subscribes and renders something on it's own. */
class Renderer[T](subscribeable: Subscribeable[T])(f: T => Html) extends SimpleComponentBase {
  override def assemble(using c: SimpleContext): Html = {
    f(subscribeable.subscribe())
  }
}
