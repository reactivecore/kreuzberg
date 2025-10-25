package kreuzberg.extras

/**
 * HTML Meta information. <meta $fieldName = $fieldData content = $content>
 */
case class Meta(
    fieldName: String,
    fieldValue: String,
    content: Option[String] = None
)

object Meta {

  def name(name: String, contentData: String): Meta = {
    Meta("name", name, Some(contentData))
  }

  def property(property: String, contentData: String): Meta = {
    Meta("property", property, Some(contentData))
  }

  def viewport(contentData: String): Meta = {
    Meta("name", "viewport", Some(contentData))
  }
}

type MetaData = Seq[Meta]

object MetaData {
  def empty: Seq[Meta] = Seq.empty
}
