package kreuzberg

import kreuzberg.{Assembly, Component, HeadlessAssembly, HeadlessComponent, Html, IdentifierFactory, KreuzbergContext}

import scala.language.implicitConversions

/** Converts Components into TreeNodes. */
private[kreuzberg] object Assembler {

  def tree(component: Component)(using KreuzbergContext): TreeNode = {
    treeFromAssembly(component, component.assemble)
  }

  def treeFromAssembly(
      component: Component,
      assembly: Assembly
  )(using ctx: KreuzbergContext): TreeNode = {
    val withId    = assembly.html.withId(component.id)
    val comment   = component.comment
    val htmlToUse = if (comment.isEmpty) {
      withId
    } else {
      withId.addComment(comment)
    }
    val children  = htmlToUse.embeddedComponents.view.map(tree).toVector ++ assembly.headless.map(treeFromHeadless)
    TreeNode(
      component,
      htmlToUse,
      children,
      assembly.handlers,
      assembly.subscriptions.flatMap(_.dependencies)
    )
  }

  def treeFromHeadless(headless: HeadlessComponent)(using KreuzbergContext): TreeNode = {
    treeFromHeadlessAssembly(headless, headless.assemble)
  }

  def treeFromHeadlessAssembly(
      headless: HeadlessComponent,
      assembly: HeadlessAssembly
  )(using ctx: KreuzbergContext): TreeNode = {
    val children = assembly.children.map(treeFromHeadless)
    TreeNode(
      component = headless,
      html = Html.Empty,
      children = children,
      handlers = assembly.handlers,
      assembly.subscriptions.flatMap(_.dependencies)
    )
  }

  /**
   * Assemble a value as a single component discarding the state. For testcases.
   */
  def single(component: () => Component): Assembly = {
    IdentifierFactory.withFresh {
      val c = component()
      c.assemble(using KreuzbergContext.empty)
    }
  }

  /**
   * Assemble a value as a single component to a tree, discarding the state. For testcases.
   */
  def singleTree(component: () => Component): TreeNode = {
    IdentifierFactory.withFresh {
      val c = component()
      tree(c)(using KreuzbergContext.empty)
    }
  }
}
