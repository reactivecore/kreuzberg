package kreuzberg.extras

import kreuzberg.Component

import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions

/** The target of a Route. */
trait RoutingTarget {

  /** Title shown while loading */
  def preTitle: String

  /** Immediately forward. */
  def forward: Option[UrlResource] = None

  /** Meta Data */
  def metaData: MetaData

  /** Load the route. */
  def load(): Future[RoutingResult]

  def transform(
      titleFn: String => String,
      metaFn: MetaData => MetaData,
      componentFn: Component => Component
  ): RoutingTarget = RoutingTarget.Transformer(this, titleFn, metaFn, componentFn)
}

/** The final result of the Route, is a RoutingTarget itself. */
case class RoutingResult(
    title: String,
    component: Component,
    meta: MetaData = MetaData.empty
) extends RoutingTarget {
  override def preTitle: String = title

  override def metaData: MetaData = meta

  override def load(): Future[RoutingResult] = Future.successful(this)

  override def transform(
      titleFn: String => String,
      metaFn: MetaData => MetaData,
      componentFn: Component => Component
  ): RoutingTarget = {
    RoutingResult(
      titleFn(title),
      componentFn(component),
      metaFn(metaData)
    )
  }
}

case class Forward(url: UrlResource) extends RoutingTarget {
  override def preTitle: String = ""

  override def metaData: MetaData = MetaData.empty

  override def load(): Future[RoutingResult] = Future.failed(new RuntimeException("Should not evaluate"))

  override def forward: Option[UrlResource] = Some(url)
}

object RoutingResult {

  /** Pages can be converted to RoutingResults. */
  implicit def fromPage(page: Page): RoutingResult = RoutingResult(page.title, page, page.metaData)
}

object RoutingTarget {

  case class Transformer(
      underlying: RoutingTarget,
      titleFn: String => String,
      metaFn: MetaData => MetaData,
      componentFn: Component => Component
  ) extends RoutingTarget {
    override def preTitle: String = titleFn(underlying.preTitle)

    override def metaData: MetaData = metaFn(underlying.metaData)

    override def load(): Future[RoutingResult] = {
      implicit given ec: ExecutionContext = ExecutionContext.parasitic
      underlying.load().map { result =>
        RoutingResult(
          title = titleFn(result.title),
          component = componentFn(result.component)
        )
      }
    }
  }
}
