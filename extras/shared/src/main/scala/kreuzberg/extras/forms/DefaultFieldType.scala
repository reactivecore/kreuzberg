package kreuzberg.extras.forms

/** Returns the default HTML field type for a type */
trait DefaultFieldType[T] {
  def fieldType: String
}

object DefaultFieldType {
  given DefaultFieldType[Int] with       {
    override def fieldType: String = "number"
  }
  given DefaultFieldType[String] with {
    override def fieldType: String = "text"
  }
}
