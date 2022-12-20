package kreuzberg.examples.showcase

/** Sample API. */
trait Lister[F[_]] {

  /** List current items. */
  def listItems(): F[Seq[String]]

  /** Add one item. */
  def addItem(item: String): F[Unit]

}
