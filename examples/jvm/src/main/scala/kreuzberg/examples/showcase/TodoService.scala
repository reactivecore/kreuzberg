package kreuzberg.examples.showcase

import kreuzberg.examples.showcase.todo.{ListItemResponse, TodoApi}
import zio.ZIO
import zio.Task

class TodoService extends TodoApi[Task] {
  private var items: Vector[String] = Vector.empty
  private object lock

  override def listItems(): Task[ListItemResponse] = {
    ZIO.attemptBlocking {
      lock.synchronized {
        ListItemResponse(
          items,
          statusCode = 200
        )
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
