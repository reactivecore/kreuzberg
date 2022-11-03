package kreuzberg

/** Return result of an [[Assembler]] operation, will be built into the Tree */
sealed trait Assembly {
  def map(f: Html => Html): Assembly = {
    this match
      case p: Assembly.Pure      => p.copy(html = f(p.html))
      case c: Assembly.Container =>
        c.copy(
          renderer = parts => f(c.renderer(parts))
        )
  }

  def bindings: Vector[EventBinding]

  def nodes: Vector[TreeNode]
}

object Assembly {
  implicit def apply(html: Html): Pure = Pure(html)

  /** Simple puts the nodes as base of the current html */
  def apply(base: Html, nodes: Vector[TreeNode], handlers: Vector[EventBinding] = Vector.empty): Container = {
    Container(nodes, htmls => base(htmls: _*), handlers)
  }

  /** Has a raw HTML representation. */
  case class Pure(html: Html, bindings: Vector[EventBinding] = Vector.empty) extends Assembly {
    override def nodes: Vector[TreeNode] = Vector.empty
  }

  /** Is a container with sub nodes. */
  case class Container(
      nodes: Vector[TreeNode],
      renderer: Vector[Html] => Html,
      bindings: Vector[EventBinding] = Vector.empty
  ) extends Assembly
}
