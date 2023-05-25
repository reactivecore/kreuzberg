package kreuzberg

/**
 * Return result of an [[Component.assemble]] operation, will be built into the Tree
 * @param html
 *   HTML Code with embedded Components
 * @param handlers
 *   Event Handlers
 */
case class Assembly(
    html: Html,
    handlers: Vector[EventBinding] = Vector.empty
)
