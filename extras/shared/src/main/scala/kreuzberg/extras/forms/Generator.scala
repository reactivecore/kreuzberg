package kreuzberg.extras.forms
import scala.quoted.*
import kreuzberg.extras.forms.Error.{DecodingError, ValidationErrorBuilder}

/** Generator for Forms. */
object Generator {

  /** Generate a Form from an annotated case class. */
  inline def generate[T]: Form[T] = {
    val fieldValidator    = buildAllFieldValidator[T]
    val mainAnnotation    = fetchMainAnnotations[T]
    val fieldDescriptions = fetchFieldDescriptions[T]
    val encoder           = buildEncoder[T]
    val decoder           = buildDecoder[T].compose[List[String]](_.toArray)

    build(fieldValidator, mainAnnotation, fieldDescriptions, encoder, decoder)
  }

  private inline def build[T](
      fieldValidator: Validator[T],
      mainValidator: List[UseValidator[T]],
      fields: List[FieldDescription[?]],
      encoder: T => List[String],
      decoder: List[String] => DecodingResult[T]
  ): Form[T] = {
    val formFields      = fields.map(buildFormField(_))
    val mergedValidator = mainValidator.foldLeft(fieldValidator) { (v, a) => v.chain(a.validator) }
    val formCodec       = Codec.fromEncoderAndDecoder(encoder, decoder)
    FixedForm(formFields, formCodec, mergedValidator)
  }

  private case class FixedForm[T](
      fields: List[FormField[?]],
      codec: Codec[T, List[String]],
      validator: Validator[T]
  ) extends Form[T]

  private inline def buildFormField[T](fieldDescription: FieldDescription[T]): FormField[T] = {
    def nonEmptyOr(candidate: UseField[T] => String, alternative: String): String = {
      fieldDescription.useFieldAnnotation.map(candidate).filter(_.nonEmpty).getOrElse(alternative)
    }
    FormField(
      name = nonEmptyOr(_.name, fieldDescription.name),
      label = nonEmptyOr(_.label, fieldDescription.name),
      placeholder = nonEmptyOr(_.placeholder, ""),
      formType = nonEmptyOr(_.ftype, fieldDescription.defaultFieldType),
      description = nonEmptyOr(_.description, ""),
      tooltip = nonEmptyOr(_.tooltip, ""),
      codec = fieldDescription.codec,
      validator = fieldDescription.useFieldAnnotation.map(_.validator).getOrElse(Validator.succeed),
      required = fieldDescription.useFieldAnnotation.exists(_.required)
    )
  }

  /** Fetch main "useValidator" Annotation if there is. */
  inline def fetchMainAnnotations[T]: List[UseValidator[T]] = {
    ${ fetchMainAnnotationsImpl[T] }
  }

  private def fetchMainAnnotationsImpl[T](using Quotes, Type[T]): Expr[List[UseValidator[T]]] = {
    val analyzed = new Analyzer().analyze[T]
    Expr.ofList(analyzed.mainAnnotation)
  }

  private case class FieldDescription[T](
      useFieldAnnotation: Option[UseField[T]],
      codec: Codec[T, String],
      name: String,
      defaultFieldType: String
  )

  private inline def fetchFieldDescriptions[T]: List[FieldDescription[?]] = {
    ${ fetchFieldDescriptionsImpl[T] }
  }

  /** Fetch field descriptions if there is */
  private def fetchFieldDescriptionsImpl[T](using Quotes, Type[T]): Expr[List[FieldDescription[?]]] = {
    val analyzer = new Analyzer()
    val analyzed = analyzer.analyze[T]

    def convert[F](field: analyzer.FieldResult[F]): Expr[FieldDescription[F]] = {
      given Type[F]         = field._type
      val wrappedAnnotation = field.annotation.fold(Expr(None))(a => '{ Some(${ a }) }.asExprOf[Option[UseField[F]]])
      '{
        FieldDescription[F](
          name = ${ Expr(field.name) },
          codec = ${ field.codec },
          useFieldAnnotation = ${ wrappedAnnotation },
          defaultFieldType = ${ field.defaultFieldType }.fieldType
        )
      }
    }
    Expr.ofList(analyzed.fields.map(convert(_)))
  }

  inline def buildEncoder[T]: T => List[String] = {
    ${ buildEncoderImpl[T] }
  }

  private def buildEncoderImpl[T](using Quotes, Type[T]): Expr[T => List[String]] = {
    val analyzer = new Analyzer()
    val analyzed = analyzer.analyze[T]
    import analyzer.quotes.reflect.*

    val fields: Expr[T] => List[Expr[String]] = instance => {
      analyzed.fields.map { field =>
        given Type[field.FieldType] = field._type
        '{ ${ field.codec }.encode(${ Select(instance.asTerm, field.declaredFieldSymbol).asExprOf[field.FieldType] }) }
      }
    }

    '{ (in: T) => ${ Expr.ofList(fields('in)) } }
  }

  inline def buildDecoder[T]: Array[String] => DecodingResult[T] = {
    ${ buildDecoderImpl[T] }
  }

  private def buildDecoderImpl[T](using Quotes, Type[T]): Expr[Array[String] => DecodingResult[T]] = {
    val analyzer = new Analyzer()
    val analyzed = analyzer.analyze[T]
    import analyzer.quotes.reflect.*

    def encodeArg[F](array: Term, field: analyzer.FieldResult[F], idx: Int): Term = {
      given Type[F] = field._type
      '{ ${ field.codec }.decodeOrThrow(${ array.asExprOf[Array[String]] }.apply(${ Expr(idx) })) }.asTerm
    }

    def encodeAllArgs(array: Term): List[Term] = {
      analyzed.fields.zipWithIndex.map { case (field, idx) =>
        encodeArg(array, field, idx)
      }
    }

    def builder(array: Term): Term = {
      Select(Ref(analyzed.companion), analyzed.applyMethod)
        .appliedToArgs(
          encodeAllArgs(array)
        )
    }

    '{ (in: Array[String]) =>
      if (in.length != ${ Expr(analyzed.fields.size) }) {
        Left(DecodingError("Unexpected Arity"))
      } else {
        try {
          Right(${ builder('in.asTerm).asExprOf[T] })
        } catch {
          case UnhandledDecodingError(e) => Left(e)
        }
      }
    }
  }

  /** Build a validator for all fields */
  inline def buildAllFieldValidator[T]: Validator[T] = {
    ${ buildAllFieldValidatorImpl[T] }
  }

  private def buildAllFieldValidatorImpl[T](using Quotes, Type[T]): Expr[Validator[T]] = {
    val analyzer = new Analyzer()
    val analyzed = analyzer.analyze[T]
    import analyzer.quotes.reflect.*

    def buildFieldCollector[F](builder: Term, value: Term, field: analyzer.FieldResult[F]): Term = {
      given Type[F] = field._type

      field.annotation match {
        case None           => '{}.asTerm
        case Some(existing) =>
          '{
            ${ builder.asExprOf[ValidationErrorBuilder] }.add(
              ${ existing }.validator.validate(
                ${ Select(value, field.declaredFieldSymbol).asExprOf[F] }
              ),
              ${ existing }.nameToUse(${ Expr(field.name) })
            )
          }.asTerm
      }
    }

    def buildAllFieldCollectors(builder: Term, value: Term): List[Term] = {
      analyzed.fields.map { field =>
        buildFieldCollector(builder, value, field)
      }
    }

    '{
      Validator.fromFunction { value =>
        val builder = ValidationErrorBuilder()
        ${ Expr.ofList(buildAllFieldCollectors('builder.asTerm, 'value.asTerm).map(_.asExprOf[Unit])) }
        builder.result
      }
    }
  }

  /** Helper for analyzing case classes for which we create Forms. */
  class Analyzer[Q <: Quotes](using val quotes: Q) {
    import quotes.reflect.*

    /** Analyzed case class. */
    case class Result[T](
        name: String,
        symbol: Symbol,
        fields: List[FieldResult[?]],
        mainAnnotation: List[Expr[UseValidator[T]]],
        companion: Symbol,
        applyMethod: Symbol
    )

    /** A Field of the case class. */
    case class FieldResult[F](
        constructorFieldSymbol: Symbol,
        declaredFieldSymbol: Symbol,
        name: String,
        annotation: Option[Expr[UseField[F]]],
        codec: Expr[Codec[F, String]],
        defaultFieldType: Expr[DefaultFieldType[F]],
        _type: Type[F]
    ) {
      type FieldType = F
    }

    def analyze[T](using Type[T]): Result[T] = {
      val tree              = TypeRepr.of[T]
      val symbol            = tree.typeSymbol
      val name              = symbol.name
      val constructorFields = symbol.primaryConstructor.paramSymss.flatten

      def analyzeField(constructorFieldSymbol: Symbol): FieldResult[?] = {
        val declaredField = symbol.declaredField(constructorFieldSymbol.name)
        val casted        = declaredField.tree match {
          case valDef: ValDef => valDef
          case _              => throw new RuntimeException("Declared Field is not a val")
        }

        val dataType  = casted.tpt.tpe
        type F
        given Type[F] = dataType.asType.asInstanceOf[Type[F]]

        val useFieldAnnotation: Option[Expr[UseField[F]]] = constructorFieldSymbol.annotations.collect {
          case term if term.tpe <:< TypeRepr.of[UseField[F]] => term.asExprOf[UseField[F]]
        } match {
          case Nil       => None
          case List(one) => Some(one)
          case multiple  =>
            throw new IllegalArgumentException(s"Multiple annotations found on ${name}")
        }

        val codec = Expr.summon[Codec[F, String]].getOrElse {
          throw new IllegalArgumentException("Could not find codec for type F" + Type.show[F])
        }

        val defaultFieldType = Expr.summon[DefaultFieldType[F]].getOrElse {
          throw new IllegalArgumentException("Could not find DefaultFieldType for F" + Type.show[F])
        }

        FieldResult(
          constructorFieldSymbol = constructorFieldSymbol,
          declaredFieldSymbol = declaredField,
          name = declaredField.name,
          annotation = useFieldAnnotation,
          codec = codec,
          defaultFieldType = defaultFieldType,
          _type = Type.of[F]
        )
      }

      val annotation = symbol.annotations

      val matching = annotation.collect {
        case a if a.tpe <:< TypeRepr.of[UseValidator[T]] => a.asExprOf[UseValidator[T]]
      }

      val fields = constructorFields.map(analyzeField)

      val companion   = tree.typeSymbol.companionModule
      val applyMethod = companion.methodMember("apply").head

      Result(
        name = symbol.name,
        symbol = symbol,
        fields = fields,
        mainAnnotation = matching,
        companion = companion,
        applyMethod = applyMethod
      )
    }
  }
}
