package org.scalajs.dom.html

import org.scalajs.dom.Element

trait Input extends Element {

  /** Text Input of input field */
  var value: String // scalafix:ok

  var checked: Boolean // scalafix:ok
}

trait Select extends Element {

  /** Selected value of select element */
  var value: String // scalafix:ok
}
