package kreuzberg.scalatags
import java.io.StringWriter
import java.io.Writer

private[scalatags] case class CommentTag(
    content: String
) extends scalatags.text.Frag {

  override def render: String = {
    val writer = new StringWriter
    writeTo(writer)
    writer.toString()
  }

  override def writeTo(strb: Writer): Unit = {
    strb.append("<!-- ")
    strb.append(content.replace("--", ""))
    strb.append(" -->")
  }
}
