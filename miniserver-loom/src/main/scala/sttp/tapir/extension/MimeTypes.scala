package sttp.tapir.extension
import sttp.model.MediaType
import sttp.tapir.internal.MimeByExtensionDB

object MimeTypes {
  def contentTypeByFileName(fileName: String): Option[MediaType] = {
    fileName.lastIndexOf('.') match {
      case -1 => None
      case n  => {
        val extension = fileName.drop(n + 1)
        MimeByExtensionDB(extension)
      }
    }
  }
}
