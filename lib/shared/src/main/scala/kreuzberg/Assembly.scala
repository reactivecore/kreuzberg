package kreuzberg

/**
 * Return result of an [[Component.assemble]] operation, will be built into the Tree
 * @param html
 *   HTML Code with embedded Components
 * @param handlers
 *   Event Handlers
 * @param provider
 *   Runtime type provider.
 */
case class Assembly[+R](
    html: Html,
    handlers: Vector[EventBinding] = Vector.empty,
    provider: RuntimeProvider[R] = _ => ()
) {

  def nodes: Vector[TreeNode] = html.embeddedNodes.toVector
}
