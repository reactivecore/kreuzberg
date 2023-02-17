package kreuzberg.scalatags

import kreuzberg.imperative.{AssemblyContext, SimpleContext}
import kreuzberg.{Assembler, Assembly, AssemblyResult, Html, TreeNode}
import scalatags.Text.TypedTag

import scala.language.implicitConversions

implicit def scalaTagsToHtml(st: TypedTag[String]): Html = {
  ScalaTagsHtml(st)
}

implicit def htmlToScalaTags(in: Html): ScalaTagsHtmlEmbed = ScalaTagsHtmlEmbed(in)

implicit def scalaTagsToAssemblyResult(st: TypedTag[String]): AssemblyResult = {
  AssemblyResult.fromHtml(scalaTagsToHtml(st))
}

implicit def scalaTagsToAssembly(st: TypedTag[String]): Assembly = {
  Assembly(scalaTagsToHtml(st))
}

extension (tn: TreeNode) {
  def wrap: ScalaTagsHtmlEmbed = ScalaTagsHtmlEmbed(Html.treeNodeToHtml(tn))
}

extension [T](component: T)(using a: Assembler[T]) {
  def wrap(implicit c: AssemblyContext): ScalaTagsHtmlEmbed = {
    c.transform(a.assembleWithNewId(component)).wrap
  }
}

// Hack to import scalatags via kreuzberg.scalatags.all._
val all = scalatags.Text.all
