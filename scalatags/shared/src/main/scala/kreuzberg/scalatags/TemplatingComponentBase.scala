package kreuzberg.scalatags

import kreuzberg.{SimpleComponentBase, SimpleContext, Subscribeable}
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*
import scala.language.implicitConversions

/**
 * Base class which auto converts Models to subscribed frags, can be used for simple templating-like components.
 */
abstract class TemplatingComponentBase extends SimpleComponentBase {

  /** Converts a model into a subscribed frag. */
  protected implicit def modelToModifier(s: Subscribeable[?])(using c: SimpleContext): Frag = {
    s.subscribe().toString
  }

  extension [T](s: Subscribeable[Seq[T]]) {

    /** Iterates all childs and creates frags for them. */
    def iter(f: T => Frag)(using c: SimpleContext): Frag = {
      SeqFrag(s.subscribe().map(f))
    }
  }

  override def assemble(using c: SimpleContext): ScalaTagsHtml
}
