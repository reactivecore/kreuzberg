package kreuzberg

import java.io.{PrintWriter, StringWriter}
import scala.concurrent.ExecutionContext

class KreuzbergExecutionContext(changer: Changer) extends ExecutionContext {
  final override def execute(runnable: Runnable): Unit = {
    changer.call(() => runnable.run())
  }

  final override def reportFailure(cause: Throwable): Unit = {
    val stringWriter = new StringWriter()
    val printWriter  = new PrintWriter(stringWriter)
    cause.printStackTrace(printWriter)
    printWriter.flush()
    Logger.warn(s"Unhandled Exception: ${stringWriter.toString}")
  }
}
