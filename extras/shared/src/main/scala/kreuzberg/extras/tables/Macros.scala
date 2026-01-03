package kreuzberg.extras.tables

import scala.compiletime.{erasedValue, summonInline}
import scala.deriving.Mirror
import scala.quoted.{Expr, Quotes, Type}

private[tables] object Macros {

  /** Build a [[Tabular]]-Implementation for a case class */
  inline def buildTabular[T <: Product](using mirror: Mirror.ProductOf[T]): Tabular[T] = {
    val annotations             = fetchAnnotations[T]
    val labels                  = deriveLabels[T]
    val columns                 = deriveColumns[mirror.MirroredElemTypes](labels, annotations)
    val splitter: T => Seq[Any] = { value => value.productIterator.toIndexedSeq }
    new TabularResult[T](columns, splitter)
  }

  class TabularResult[T](val columns: Seq[TableColumn[?]], splitter: T => Seq[Any]) extends Tabular[T] {
    override def row(value: T): TableRow = TableRow(splitter(value))
  }

  inline def deriveColumns[T](
      labels: List[String],
      annotations: List[Option[UseTableColumn[?]]]
  ): List[TableColumn[?]] = {
    inline erasedValue[T] match {
      case _: EmptyTuple => Nil
      case _: (t *: ts)  =>
        val label :: labelTail           = labels: @unchecked
        val annotation :: annotationTail = annotations: @unchecked
        deriveColumn[t](label, annotation.asInstanceOf[Option[UseTableColumn[t]]]) :: deriveColumns[ts](
          labelTail,
          annotationTail
        )
    }
  }

  inline def deriveColumn[F](name: String, inline annotation: Option[UseTableColumn[F]]): TableColumn[F] = {
    val label = annotation.map(_.columnName).filter(_.nonEmpty).getOrElse(name)

    val renderer: CellRenderer[F] = annotation.flatMap(_.renderer).getOrElse {
      summonInline[CellRenderer[F]]
    }

    val ordering: Option[Ordering[F]] = if (annotation.exists(_.hasOrdering == false)) {
      None
    } else {
      annotation.flatMap(_.orderingOverride).orElse {
        Some(summonInline[Ordering[F]])
      }
    }

    val thClasses = annotation.map(_.thClassesOverride).getOrElse("")

    TableColumn(
      name = label,
      renderer = renderer,
      ordering = ordering,
      thClassesOverride = thClasses
    )
  }

  inline def deriveLabels[T](using m: Mirror.Of[T]): List[String] = {
    summonLabels[m.MirroredElemLabels]
  }

  inline def summonLabels[T <: Tuple]: List[String] = {
    inline erasedValue[T] match {
      case _: EmptyTuple => Nil
      case _: (t *: ts)  => summonInline[ValueOf[t]].value.asInstanceOf[String] :: summonLabels[ts]
    }
  }

  /** Extract table name annotation for the type. */
  inline def fetchAnnotations[T]: List[Option[UseTableColumn[?]]] = {
    ${ fetchAnnotationsImpl[T] }
  }

  def fetchAnnotationsImpl[T](using quotes: Quotes, t: Type[T]): Expr[List[Option[UseTableColumn[?]]]] = {
    import quotes.reflect.*
    val tree   = TypeRepr.of[T]
    val symbol = tree.typeSymbol

    Expr.ofList(
      symbol.primaryConstructor.paramSymss.flatten
        .map { sym =>
          val declaredField = symbol.declaredField(sym.name)
          val casted        = declaredField.tree match {
            case valDef: ValDef => valDef
            case _              => throw new RuntimeException("Declared Field is not a val")
          }

          val dataType  = casted.tpt.tpe
          type F
          given Type[F] = dataType.asType.asInstanceOf[Type[F]]

          sym.annotations.collectFirst {
            case term if (term.tpe <:< TypeRepr.of[UseTableColumn[F]]) =>
              term.asExprOf[UseTableColumn[F]]
            case term if (term.tpe <:< TypeRepr.of[UseTableColumn[?]]) =>
              val s = s"Invalid column for type ${dataType.show} found: ${term.show}, do you use the correct type?"
              quotes.reflect.report.errorAndAbort(s)
          } match {
            case None    => '{ None }
            case Some(e) => '{ Some(${ e }) }
          }
        }
    )
  }
}
