package kreuzberg.extras.forms

/** A Single Field Element. */
case class FormField[T](
    name: String,
    label: String = "",
    placeholder: String = "",
    formType: String = "text",
    codec: Codec[T, String] = Codec.simpleString,
    validator: Validator[T] = Validator.succeed,
    required: Boolean = false
) {
  def ::[Y](before: FormField[Y]): RecursiveFormFields[Y *: T *: EmptyTuple] = {
    RecursiveFormFields.Node(before, RecursiveFormFields.Node(this, RecursiveFormFields.Leaf))
  }

  def decodeAndValidate(in: String): Result[T] = {
    for {
      value <- codec.decode(in)
      _     <- validator.validated(value)
    } yield {
      value
    }
  }
}

/** RecursiveFormElements based upon a Tuple. */
sealed trait RecursiveFormFields[T <: Tuple] extends Form[T] {
  def ::[Y](before: FormField[Y]): RecursiveFormFields[Y *: T] = {
    RecursiveFormFields.Node(before, this)
  }
}

object RecursiveFormFields {

  object Leaf extends RecursiveFormFields[EmptyTuple] {
    override def fields: List[FormField[?]] = Nil

    override object codec extends Codec[EmptyTuple, List[String]] {
      override def encode(value: EmptyTuple): List[String] = Nil

      override def decode(encoded: List[String]): DecodingResult[EmptyTuple] = Right(EmptyTuple)
    }

    override def validator: Validator[EmptyTuple] = Validator.succeed
  }

  case class Node[T, R <: Tuple](head: FormField[T], remaining: RecursiveFormFields[R])
      extends RecursiveFormFields[T *: R] {
    override def fields: List[FormField[?]] = head :: remaining.fields

    object codec extends Codec[T *: R, List[String]] {
      override def encode(value: T *: R): List[String] = {
        head.codec.encode(value.head) :: remaining.codec.encode(value.tail)
      }

      override def decode(encoded: List[String]): DecodingResult[T *: R] = {
        encoded match {
          case headString :: tailStrings =>
            for {
              headDecoded <- head.codec.decode(headString)
              tailDecoded <- remaining.codec.decode(tailStrings)
            } yield {
              headDecoded *: tailDecoded
            }
          case _                         =>
            Left(Error.DecodingError("Argument list too short"))
        }
      }
    }

    override def validator: Validator[T *: R] = { in =>
      Error.ValidationError.combineOpt(
        head.validator.validate(in.head).map(_.nest(head.name)),
        remaining.validator.validate(in.tail)
      )
    }
  }
}
