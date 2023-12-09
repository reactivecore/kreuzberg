package kreuzberg.rpc

import scala.annotation.StaticAnnotation

/** Annotation to set the API name */
case class ApiName(name: String) extends StaticAnnotation
