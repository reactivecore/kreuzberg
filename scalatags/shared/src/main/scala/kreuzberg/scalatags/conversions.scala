package kreuzberg.scalatags

import kreuzberg.imperative.{AssemblyContext, SimpleContext}
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
  case tn: TreeNode => ScalaTagsTreeNodeEmbedding(tn)
  case h: Html      => ScalaTagsHtmlEmbedding(h)
}

implicit def scalaTagsToAssemblyResult(st: TypedTag[String]): AssemblyResult[Unit] = {
  AssemblyResult.fromHtml(scalaTagsToHtml(st))
}

implicit def scalaTagsToAssembly(st: TypedTag[String]): Assembly[Unit] = {
  Assembly(scalaTagsToHtml(st))
}

extension (tn: TreeNode) {
  def wrap: ScalaTagsTreeNodeEmbedding = ScalaTagsTreeNodeEmbedding(tn)
}

extension (component: Component) {
  def wrap(implicit c: AssemblyContext): ScalaTagsTreeNodeEmbedding = {
    c.transform(Assembler.assembleWithNewId(component)).wrap
  }
}

// Hack to import scalatags via kreuzberg.scalatags.all._
val all = scalatags.Text.all
