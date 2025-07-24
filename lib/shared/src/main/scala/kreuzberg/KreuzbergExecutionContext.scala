package kreuzberg

import java.io.{PrintWriter, StringWriter}
import scala.concurrent.ExecutionContext

trait KreuzbergExecutionContext extends ExecutionContext {
  self: Changer =>

  final override def execute(runnable: Runnable): Unit = {
    call(() => runnable.run())
  }

  final override def reportFailure(cause: Throwable): Unit = {
    val stringWriter = new StringWriter()
    val printWriter  = new PrintWriter(stringWriter)
    cause.printStackTrace(printWriter)
    printWriter.flush()
    Logger.warn(s"Unhandled Exception: ${stringWriter.toString}")
  }
}
