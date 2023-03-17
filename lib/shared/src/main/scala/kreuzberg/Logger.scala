package kreuzberg

object Logger {
  private var _isDebug = false
  private var _isTrace = false

  inline def isDebug: Boolean = _isDebug
  inline def isTrace: Boolean = _isTrace

  def enableDebug(): Unit = {
    _isDebug = true
  }

  def enableTrace(): Unit = {
    _isTrace = true
  }

  def trace(str: => String): Unit = {
    if (isTrace) {
      println(s"Trace: ${str}")
    }
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
