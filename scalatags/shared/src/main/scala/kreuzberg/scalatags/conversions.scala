package kreuzberg.scalatags

import kreuzberg.imperative.SimpleContext
import kreuzberg.{Assembler, Assembly, AssemblyResult, Html, TreeNode}
import scalatags.Text.TypedTag

import scala.language.implicitConversions
import kreuzberg.Component
import kreuzberg.HtmlEmbedding
import kreuzberg.ComponentNode

implicit def scalaTagsToHtml(st: TypedTag[String]): Html = {
  ScalaTagsHtml(st)
}

implicit def htmlEmbed(in: HtmlEmbedding): ScalaTagsEmbedding = in match {
  case tn: Component => ScalaTagsComponentEmbedding(tn)
  case h: Html       => ScalaTagsHtmlEmbedding(h)
}

implicit def scalaTagsToAssemblyResult(st: TypedTag[String]): AssemblyResult = {
  AssemblyResult.fromHtml(scalaTagsToHtml(st))
}

implicit def scalaTagsToAssembly(st: TypedTag[String]): Assembly = {
  Assembly(scalaTagsToHtml(st))
}

extension (tn: Component) {
  def wrap: ScalaTagsComponentEmbedding = ScalaTagsComponentEmbedding(tn)
}

// Hack to import scalatags via kreuzberg.scalatags.all._
val all = scalatags.Text.all
