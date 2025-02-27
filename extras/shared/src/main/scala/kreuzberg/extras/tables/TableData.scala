package kreuzberg.extras.tables

import kreuzberg.Html

/** A table of data of raw data */
case class TableData(
    columns: Seq[TableColumn[?]],
    rows: Seq[TableRow]
) {

  inline def rowCount: Int = rows.size

  inline def columnCount: Int = columns.size

  def orderBy(columnIdx: Int, descending: Boolean): TableData = {
    val ordered = for {
      column   <- columns.unapply(columnIdx)
      ordering <- column.rowOrdering(columnIdx, descending)
    } yield {
      copy(
        rows = rows.sorted(using ordering)
      )
    }
    ordered.getOrElse(this)
  }
}

/** Defines a single table column. */
case class TableColumn[T](
    name: String,
    renderer: CellRenderer[T] = CellRenderer.default,
    ordering: Option[Ordering[T]] = None,
    thClassesOverride: String = ""
) {

  /**
   * Builds a row ordering for this column.
   *
   * Note: may crash if columnIdx is out of range.
   */
  def rowOrdering(columnIdx: Int, descending: Boolean): Option[Ordering[TableRow]] = {
    for {
      ordering     <- ordering
      maybeReversed = if (descending) {
                        ordering.reverse
                      } else {
                        ordering
                      }
    } yield {
      Ordering.by[TableRow, T] { row =>
        row.values(columnIdx).asInstanceOf[T]
      }(using maybeReversed)
    }
  }

  /**
   * Renders a single field
   * @return
   *   classOverride and value
   */
  def render(value: Any): (String, Html) = {
    val casted        = value.asInstanceOf[T]
    val classOverride = renderer.classOverride(casted)
    val html          = renderer.render(casted)
    (classOverride, html)
  }
}

/**
 * A Single row inside a table.
 *
 * Must have the same number of elements as columns
 */
case class TableRow(values: Seq[Any])
