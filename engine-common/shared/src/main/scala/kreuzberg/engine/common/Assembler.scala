package kreuzberg.engine.common

import kreuzberg.dom.ScalaJsNode
import kreuzberg.{AssemblerContext, Assembly, Component, IdentifierFactory}

import scala.language.implicitConversions

object Assembler {

  def tree[T <: Component](component: T)(using AssemblerContext): ComponentNode[T] = {
    treeFromAssembly(component, component.assemble)
  }

  def treeFromAssembly[T <: Component](
      component: T,
      assembly: Assembly
  )(using ctx: AssemblerContext): ComponentNode[T] = {
    val withId    = assembly.html.withId(component.id)
    val comment   = component.comment
    val htmlToUse = if (comment.isEmpty()) {
      withId
    } else {
      withId.addComment(comment)
    }
    val children  = htmlToUse.embeddedComponents.view.map { component =>
      tree(component)
    }.toVector
    ComponentNode(
      component,
      htmlToUse,
      children,
      assembly.handlers,
      assembly.subscriptions.map(_.id)
    )
  }

  /**
   * Assemble a value as a single component discarding the state. For testcases.
   */
  def single[T <: Component](component: () => T): Assembly = {
    IdentifierFactory.withFresh {
      val c = component()
      c.assemble(using AssemblerContext.empty)
    }
  }

  /**
   * Assemble a value as a single component to a tree, discarding the state. For testcases.
   */
  def singleTree[T <: Component](component: () => T): ComponentNode[T] = {
    IdentifierFactory.withFresh {
      val c = component()
      tree(c)(using AssemblerContext.empty)
    }
  }
}
