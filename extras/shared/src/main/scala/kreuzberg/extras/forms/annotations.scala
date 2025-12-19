package kreuzberg.extras.forms

import scala.annotation.StaticAnnotation

/** Annotation to control field generation. */
case class UseField[-T](
    name: String = "",
    label: String = "",
    placeholder: String = "",
    ftype: String = "",
    description: String = "",
    tooltip: String = "",
    validator: Validator[T] = Validator.succeed,
    required: Boolean = false
) extends StaticAnnotation {

  /** Returns the name to use for this field. */
  def nameToUse(fieldName: String): String = {
    if (name.isEmpty) {
      fieldName
    } else {
      name
    }
  }
}

/** Annotation to control main validator. */
case class UseValidator[-T](validator: Validator[T]) extends StaticAnnotation
