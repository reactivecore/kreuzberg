package kreuzberg.examples.showcase

import kreuzberg.examples.showcase.todo.{ListItemResponse, TodoApi}
import kreuzberg.miniserver.loom.MiniServer
import kreuzberg.miniserver.{AssetCandidatePath, AssetPaths, MiniServerConfig}
import kreuzberg.rpc.{Dispatcher, Id, SecurityError}

import scala.annotation.experimental

class TodoServiceLoom extends TodoApi[Id] {
  private var items: Vector[String] = Vector.empty

  private object lock

  override def listItems(): ListItemResponse = {
    lock.synchronized {
      ListItemResponse(
        items,
        statusCode = 200
      )
    }
  }

  override def addItem(item: String): Unit = {
    lock.synchronized {
      items = items :+ item
    }
  }
}

@experimental
object ServerMainLoom extends App {
  val todoDispatcher: Dispatcher[Id] =
    Dispatcher.makeIdDispatcher[TodoApi[Id]](new TodoServiceLoom: TodoApi[Id]).preRequestFlatMap { request =>
      // Demonstrating adding a pre filter
      // Note: Headers are lower cased
      val id = request.headers.collectFirst {
        case (key, value) if key == "x-client-id" => value
      }
      id match {
        case None => throw new SecurityError("Missing client id")
        case _    => request
      }
    }

  val config = MiniServerConfig[Id](
    AssetPaths(
      Seq(
        AssetCandidatePath("examples/js/target/client_bundle/client/fast"),
        AssetCandidatePath("../examples/js/target/client_bundle/client/fast"),
        AssetCandidatePath("../../examples/js/target/client_bundle/client/fast")
      )
    ),
    api = Some(todoDispatcher)
  )

  val miniServer = MiniServer(config)
  miniServer.run()
}
