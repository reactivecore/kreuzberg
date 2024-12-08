package kreuzberg.examples.showcase

import kreuzberg.examples.showcase.todo.{ListItemResponse, TodoApi}
import kreuzberg.miniserver.loom.MiniServer
import kreuzberg.miniserver.{AssetCandidatePath, AssetPaths, MiniServerConfig}
import kreuzberg.rpc.{Dispatcher, Id, SecurityError}

import java.util.UUID
import scala.annotation.experimental
import io.circe.syntax.*

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

  // Demonstrating initialization of JS Side with some data of the Server
  val initializer: () => String = { () =>
    val data   = InitData(code = UUID.randomUUID().toString)
    val asJson = data.asJson.spaces2
    asJson
  }

  val config = MiniServerConfig[Id](
    deploymentConfig,
    api = Some(todoDispatcher),
    init = Some(initializer)
  )

  val miniServer = MiniServer(config)
  miniServer.run()
}
