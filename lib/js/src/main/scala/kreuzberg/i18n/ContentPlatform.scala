package kreuzberg.i18n

import org.scalajs.dom

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js.URIUtils
import scala.util.Failure

/** Browser side: fetches rendered content from the MiniServer, once per document and page. */
private[i18n] object ContentPlatform {
  private given ExecutionContext = scala.scalajs.concurrent.JSExecutionContext.queue

  private val cache = mutable.Map[String, Future[String]]()

  def load(name: String): Future[String] = {
    cache.getOrElseUpdate(
      name,
      fetchContent(name).andThen { case Failure(_) =>
        // Retry on the next attempt
        cache.remove(name)
      }
    )
  }

  private def fetchContent(name: String): Future[String] = {
    val url = LocalizedContent.EndpointPath.mkString("/", "/", "") +
      s"?name=${URIUtils.encodeURIComponent(name)}&locale=${URIUtils.encodeURIComponent(Translations.current.locale)}"
    dom.fetch(url).toFuture.flatMap { response =>
      if (response.ok) {
        response.text().toFuture
      } else {
        Future.failed(new NoSuchElementException(s"Could not load content ${name}: HTTP ${response.status}"))
      }
    }
  }
}
