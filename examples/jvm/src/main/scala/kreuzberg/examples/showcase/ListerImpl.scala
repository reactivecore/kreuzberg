package kreuzberg.examples.showcase
import kreuzberg.rpc.ServiceExecutionError
import zio.IO
import zio.ZIO
import zio.Task

class ListerImpl extends Lister[Task] {
  private var items: Vector[String] = Vector.empty
  private object lock

  override def listItems(): Task[Seq[String]] = {
    ZIO.attemptBlocking {
      lock.synchronized {
        items
      }
    }
  }

  override def addItem(item: String): Task[Unit] = {
    ZIO.attemptBlocking {
      lock.synchronized {
        items = items :+ item
      }
    }
  }
}
