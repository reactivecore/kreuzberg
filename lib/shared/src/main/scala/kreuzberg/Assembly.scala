package kreuzberg

/** Return result of an [[Assembler]] operation, will be built into the Tree */
sealed trait Assembly[+R] {
  def map(f: Html => Html): Assembly[R] = {
    this match
      case p: Assembly.Pure[_]      => p.copy(html = f(p.html))
      case c: Assembly.Container[_] =>
        c.copy(
          renderer = parts => f(c.renderer(parts))
        )
  }

  def mapRuntime[R2](f: R => R2): Assembly[R2]

  def bindings: Vector[EventBinding]

  def nodes: Vector[TreeNode]

  def renderWithId(id: ComponentId): Html

  def provider: RuntimeProvider[R]
}

object Assembly {
  implicit def apply(html: Html): Pure[Unit] = Pure(html)

  /** Simple puts the nodes as base of the current html */
  def apply(base: Html, nodes: Vector[TreeNode], handlers: Vector[EventBinding] = Vector.empty): Container[Unit] = {
    Container(nodes, htmls => base.addInner(htmls), handlers)
  }

  /** Has a raw HTML representation. */
  case class Pure[+R](html: Html, bindings: Vector[EventBinding] = Vector.empty, provider: RuntimeProvider[R] = _ => ())
      extends Assembly[R] {
    override def nodes: Vector[TreeNode] = Vector.empty

    override def renderWithId(id: ComponentId): Html = {
      html.withId(id)
    }

    override def mapRuntime[R2](f: R => R2): Assembly[R2] = {
      copy(
        provider = provider.andThen(f)
      )
    }
  }

  /** Is a container with sub nodes. */
  case class Container[+R](
      nodes: Vector[TreeNode],
      renderer: Vector[Html] => Html,
      bindings: Vector[EventBinding] = Vector.empty,
      provider: RuntimeProvider[R] = _ => ()
  ) extends Assembly[R] {
    override def renderWithId(id: ComponentId): Html = {
      renderer(nodes.map(_.render())).withId(id)
    }

    override def mapRuntime[R2](f: R => R2): Assembly[R2] = {
      copy(
        provider = provider.andThen(f)
      )
    }
  }
}
