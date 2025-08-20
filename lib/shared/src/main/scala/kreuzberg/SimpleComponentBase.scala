package kreuzberg

/**
 * A Component base which uses a more imperative [[KreuzbergContext]] for collecting event handlers and model
 * subscriptions.
 */
abstract class SimpleComponentBase extends SimpleContextDsl with Component {
  def assemble(using simpleContext: SimpleContext): Html

  override final def assemble: Assembly = {
    val sc   = new SimpleContext()
    val html = assemble(using sc)

    Assembly(
      html = html,
      handlers = sc.eventBindings(),
      subscriptions = sc.subscriptions(),
      headless = sc.services()
    )
  }
}
