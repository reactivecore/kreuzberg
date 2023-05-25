package kreuzberg

import kreuzberg.dom.ScalaJsNode
import kreuzberg.util.Stateful

import scala.language.implicitConversions

object Assembler {

  def tree[T <: Component](component: T): NodeResult[T] = {
    for {
      assembly <- component.assemble
      tree     <- treeFromAssembly(component, assembly)
    } yield tree
  }

  private def treeFromAssembly[T <: Component](
      component: T,
      assembly: Assembly
  ): NodeResult[T] = {
    val withId    = assembly.html.withId(component.id)
    val comment   = component.comment
    val htmlToUse = if (comment.isEmpty()) {
      withId
    } else {
      withId.addComment(comment)
    }
    val flat      = htmlToUse.flat()

    for {
      children <- Stateful.accumulate(htmlToUse.embeddedNodes) { component =>
                    tree(component)
                  }
    } yield {
      ComponentNode(
        component,
        flat,
        children,
        assembly.handlers
      )
    }
  }

  /**
   * Assemble a value as a single component discarding the state. For testcases.
   */
  def single[T <: Component](component: () => T): Assembly = {
    IdentifierFactory.withFresh {
      val c = component()
      c.assemble(AssemblyState())._2
    }
  }

  /**
   * Assemble a value as a single component to a tree, discarding the state. For testcases.
   */
  def singleTree[T <: Component](component: () => T): ComponentNode[T] = {
    IdentifierFactory.withFresh {
      val c = component()
      tree(c)(AssemblyState())._2
    }
  }
}
