package kreuzberg

object Logger {
  private var _isDebug = false
  
  inline def isDebug: Boolean = _isDebug

  def enableDebug(): Unit = {
    _isDebug = true
  }

  def debug(str: => String): Unit = {
    if (isDebug) {
      println(s"Debug: ${str}")
    }
  }

  def info(str: => String): Unit = {
    if (isDebug) {
      println(s"Info: ${str}")
    }
  }

  def warn(str: String): Unit = {
    println(s"Warn: ${str}")
  }
}
