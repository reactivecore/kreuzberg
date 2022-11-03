package kreuzberg

import kreuzberg.util.Stateful
import scalatags.Text.TypedTag

type Html = TypedTag[String]

type ScalaJsEvent = org.scalajs.dom.Event

type AssemblyResult = Stateful[AssemblyState, Assembly]

type NodeResult[T] = Stateful[AssemblyState, ComponentNode[T]]
