package kreuzberg.extras.tables

import scala.deriving.Mirror
import scala.quoted.*

/** Type class which can convert Seq[T] into a table */
trait Tabular[T] {

  /** Returns the needed columns. */
  def columns: Seq[TableColumn[?]]

  /** Converts a single element to a row */
  def row(value: T): TableRow

  /** Returns a sequence of elements to a row. */
  def toTable(values: Seq[T]): TableData = TableData(
    columns,
    values.map(row)
  )
}

object Tabular {
  inline def derived[T <: Product: Mirror.ProductOf]: Tabular[T] = Macros.buildTabular[T]
}
