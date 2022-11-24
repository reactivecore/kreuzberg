package kreuzberg

object Logger {
  private var isDebug = false

  def enableDebug(): Unit = {
    isDebug = true
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
