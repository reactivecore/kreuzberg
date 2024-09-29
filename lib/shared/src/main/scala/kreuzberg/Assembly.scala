package kreuzberg

/**
 * Return result of an [[Component.assemble]] operation, will be built into the Tree
 * @param html
 *   HTML Code with embedded Components
 * @param handlers
 *   Event Handlers
 * @param subscriptions
 *   Component Subscriptions
 * @param headless
 *   Headless children
 */
case class Assembly(
    html: Html,
    handlers: Vector[EventBinding[?]] = Vector.empty,
    subscriptions: Vector[Subscribeable[?]] = Vector.empty,
    headless: Vector[HeadlessComponent] = Vector.empty
)
