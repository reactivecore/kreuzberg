package kreuzberg.scalatags

import kreuzberg.{Assembly, Html}
import scalatags.Text.TypedTag

import scala.language.implicitConversions
import scala.util.NotGiven
import kreuzberg.Component
import kreuzberg.HtmlEmbedding

implicit def scalaTagsToHtml(st: TypedTag[String]): Html = {
  ScalaTagsHtml(st)
}

implicit def htmlEmbed(in: HtmlEmbedding): ScalaTagsEmbedding = in match {
  case tn: Component => ScalaTagsComponentEmbedding(tn)
  case h: Html       => ScalaTagsHtmlEmbedding(h)
}

implicit def scalaTagsToAssembly(st: TypedTag[String]): Assembly = {
  Assembly(scalaTagsToHtml(st))
}

extension (tn: Component) {
  def wrap: ScalaTagsComponentEmbedding = ScalaTagsComponentEmbedding(tn)
}

/**
 * Makes ScalaTags available via `import kreuzberg.scalatags.*`.
 *
 * The `*Node` conversions are excluded: ScalaTags ranks them against the `*Frag` conversions by trait inheritance,
 * which an export flattens, so both would be ambiguous. They are redefined below, only applying to non-frag elements.
 */
export scalatags.Text.all.{SeqNode as _, OptionNode as _, ArrayNode as _, *}

/** Like ScalaTags `SeqNode`, but only for elements which are no frags, which are covered by `SeqFrag`. */
implicit def SeqNode[A](xs: Seq[A])(using ev: A => Modifier, nf: NotGiven[A => Frag]): Modifier = {
  scalatags.Text.all.SeqNode(xs)
}

/** Like ScalaTags `OptionNode`, but only for elements which are no frags. */
implicit def OptionNode[A](xs: Option[A])(using ev: A => Modifier, nf: NotGiven[A => Frag]): Modifier = {
  scalatags.Text.all.OptionNode(xs)
}

/** Like ScalaTags `ArrayNode`, but only for elements which are no frags. */
implicit def ArrayNode[A](xs: Array[A])(using ev: A => Modifier, nf: NotGiven[A => Frag]): Modifier = {
  scalatags.Text.all.ArrayNode(xs)
}

/**
 * ScalaTags for qualified access, e.g. `all.span` if `span` is shadowed.
 *
 * Don't import `all.*` together with `kreuzberg.scalatags.*`, this leads to ambiguous references.
 */
val all = scalatags.Text.all
