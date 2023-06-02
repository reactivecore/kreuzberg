package kreuzberg

/**
 * Return result of an [[Component.assemble]] operation, will be built into the Tree
 * @param html
 *   HTML Code with embedded Components
 * @param handlers
 *   Event Handlers
 * @param subscriptions
 *   Component Subscriptions
 */
case class Assembly(
    html: Html,
    handlers: Vector[EventBinding] = Vector.empty,
    subscriptions: Vector[Model[_]] = Vector.empty
)
