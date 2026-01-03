package kreuzberg.extras

import kreuzberg.Component

/**
 * A Page is a component with Title.
 *
 * It can be implicitly converted to a [[RoutingResult]]
 */
trait Page extends Component {

  /** The page title */
  def title: String

  /** The metadata of the page. */
  def metaData: MetaData = MetaData.empty

}
