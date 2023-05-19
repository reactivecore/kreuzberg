package kreuzberg

import kreuzberg.dom.ScalaJsNode
import kreuzberg.util.Stateful

import scala.language.implicitConversions

object Assembler {

  def tree[R, T <: Component.Aux[R]](component: T): NodeResult[R, T] = {
    for {
      assembly <- component.assemble
      tree     <- treeFromAssembly(component, assembly)
    } yield tree
  }

  private def treeFromAssembly[R, T <: Component.Aux[R]](
      component: T,
      assembly: Assembly[R]
  ): NodeResult[R, T] = {
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
        assembly.provider,
        assembly.handlers
      )
    }
  }

  /**
   * Assemble a value as a single component discarding the state. For testcases.
   */
  def single[R, T <: Component.Aux[R]](component: () => T): Assembly[R] = {
    IdentifierFactory.withFresh {
      val c = component()
      c.assemble(AssemblyState())._2
    }
  }

  /**
   * Assemble a value as a single component to a tree, discarding the state. For testcases.
   */
  def singleTree[R, T <: Component.Aux[R]](component: () => T): ComponentNode[R, T] = {
    IdentifierFactory.withFresh {
      val c = component()
      tree(c)(AssemblyState())._2
    }
  }
}
