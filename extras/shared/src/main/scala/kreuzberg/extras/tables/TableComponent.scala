package kreuzberg.extras.tables

import kreuzberg.*
import kreuzberg.extras.ScalaTagHelpers.{cssClasses, cssClassesWithOverride}
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

/** A Component which renders a table from data and allows the user to sort. */
class TableComponent(
    dataSource: Subscribeable[TableData],
    stateModel: Model[TableComponent.State] = Model.create(TableComponent.State()),
    options: TableComponent.Options = TableComponent.Options()
) extends SimpleComponentBase {

  override def assemble(using c: SimpleContext): Html = {
    val data  = dataSource.subscribe()
    val state = stateModel.subscribe()

    val orderedData = state.orderBy
      .map { orderIdx =>
        data.orderBy(orderIdx, state.descending)
      }
      .getOrElse(data)

    table(
      cls := options.tableClasses,
      thead(
        tr(
          orderedData.columns.view.zipWithIndex.map { case (column, idx) =>
            val flipper =
              TableComponent.ColumnHeader(column.name, idx, stateModel, options, clickable = column.ordering.isDefined)
            if (column.ordering.isDefined) {
              addHandlerAny(flipper.onClick) {
                stateModel.set(state.flipSorting(idx))
              }
            }
            th(
              cssClassesWithOverride(column.thClassesOverride, options.thDefaultClasses),
              flipper
            )
          }.toSeq
        )
      ),
      tbody(
        orderedData.rows.map { row =>
          tr(
            row.values.view
              .zip(data.columns)
              .map { case (value, column) =>

                val (classOverride, html) = column.render(value)
                td(cssClassesWithOverride(classOverride, options.tdDefaultClasses), html)
              }
              .toSeq
          )
        }
      )
    )
  }
}

/** Generic variant which automatically wraps in Tabular. */
class GenericTableComponent[T](
    dataSource: Subscribeable[Seq[T]],
    stateModel: Model[TableComponent.State] = Model.create(TableComponent.State()),
    options: TableComponent.Options = TableComponent.Options()
)(using tabular: Tabular[T])
    extends TableComponent(dataSource = dataSource.map(tabular.toTable), stateModel = stateModel, options = options)

object TableComponent {

  case class Options(
      tableClasses: String = "",
      flipperButtonClasses: String = "",
      thDefaultClasses: String = "",
      tdDefaultClasses: String = "",
      undecided: Modifier = (),
      ascending: Modifier = "↓",
      descending: Modifier = "↑"
  )

  class ColumnHeader(
      columnName: String,
      columnIdx: Int,
      stateSubscribable: Subscribeable[State],
      options: Options,
      clickable: Boolean
  ) extends SimpleComponentBase {
    override def assemble(using c: SimpleContext): Html = {
      val state             = stateSubscribable.subscribe()
      val icon: Modifier    = if (state.orderBy.contains(columnIdx)) {
        if (state.descending) {
          options.descending
        } else {
          options.ascending
        }
      } else {
        options.undecided
      }
      val clickableModifier: Modifier = if (clickable) {
        cursor := "pointer"
      } else {
        ()
      }
      all.span(
        cssClasses(options.flipperButtonClasses),
        clickableModifier,
        columnName,
        icon
      )
    }

    val onClick = jsEvent("click")
  }

  case class State(
      orderBy: Option[Int] = None,
      descending: Boolean = false
  ) {
    def flipSorting(columnIdx: Int): State = {
      if (orderBy.contains(columnIdx)) {
        copy(
          descending = !descending
        )
      } else {
        State(
          orderBy = Some(columnIdx)
        )
      }
    }
  }
}
