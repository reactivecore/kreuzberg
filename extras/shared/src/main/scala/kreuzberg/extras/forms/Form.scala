package kreuzberg.extras.forms

/** Encapsulates the whole form for some value T */
trait Form[T] {

  /** The fields of a form */
  def fields: List[FormField[?]]

  /** Codec for form values. */
  def codec: Codec[T, List[String]]

  /** Validator for the form. */
  def validator: Validator[T]

  /** Maps to another type. */
  def xmap[U](mapFn: T => U, contraMapFn: U => T): Form[U] = {
    val underlying: Codec[U, List[String]] = codec.xmap(mapFn, contraMapFn)
    val underlyingValidator: Validator[U]  = validator.contraMap(contraMapFn)

    new Form[U] {
      override def fields: List[FormField[?]] = Form.this.fields

      override def codec: Codec[U, List[String]] = underlying

      override def validator: Validator[U] = underlyingValidator
    }
  }

  /** Add another validator */
  def chainValidator(validator: Validator[T]): Form[T] = {
    val chainedValidator = this.validator.chain(validator)
    new Form[T] {
      override def fields: List[FormField[?]] = Form.this.fields

      override def codec: Codec[T, List[String]] = Form.this.codec

      override def validator: Validator[T] = chainedValidator
    }
  }
}

object Form {

  /** Generate a form using macros. */
  inline def derived[T]: Form[T] = Generator.generate[T]
}
