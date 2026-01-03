package kreuzberg.extras

import kreuzberg.*
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

object Router {

  /** Main Channel for Triggering Goto-Operatoins. */
  val gotoChannel: Channel[UrlResource] = Channel.create()

  /** Go to a specific URL */
  def goto(url: UrlResource): Unit = gotoChannel(url)

  /** Force a reload. */
  val reloadChannel: Channel[Any] = Channel.create()

  /** Trigger a reload. */
  def reload(): Unit = {
    reloadChannel()
  }

  /** Event sink for going to root (e.g. on logout) */
  def gotoRoot(): Unit = goto(UrlResource())

  /** Tracks the current URL. Updated by [[MainRouter]] */
  val currentUrl: Model[UrlResource] = Model.create(UrlResource())

  /** We are loading an URL in the moment, updated by any Router. */
  val loading: Model[Boolean] = Model.create(false)

  /** Some (SubRouter) requests a specific title. */
  val requestTitle: Channel[String] = Channel.create()
}
