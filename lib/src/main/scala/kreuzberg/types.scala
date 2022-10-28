package kreuzberg

import kreuzberg.util.Stateful
import scalatags.Text.TypedTag

type Html = TypedTag[String]

type ScalaJsEvent = org.scalajs.dom.Event

type AssemblyResult = Stateful[AssemblyState, Assembly]

type RepResult[T] = Stateful[AssemblyState, Rep[T]]
