package kreuzberg.extras.tables

import scala.annotation.StaticAnnotation

/**
 * Annotation which controls the generation of [[TableColumn]] for a case class field.
 * @param columnName
 *   can be used to overwrite the columnName
 * @param hasOrdering
 *   can be used to disable ordering
 * @param orderingOverride
 *   can be used to specifiy an ordering other than default ordering
 * @param renderer
 *   can be used to set a renderer
 * @param thClassesOverride
 *   overrides default classes for `th`-Element
 */
case class UseTableColumn[T](
    columnName: String = "",
    hasOrdering: Boolean = true,
    orderingOverride: Option[Ordering[T]] = None,
    renderer: Option[CellRenderer[T]] = None,
    thClassesOverride: String = ""
) extends StaticAnnotation
