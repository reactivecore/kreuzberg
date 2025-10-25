package kreuzberg.examples.showcase

import kreuzberg.examples.showcase.todo.{ListItemResponse, TodoApi}
import kreuzberg.rpc.{Dispatcher, Id, SecurityError}

import java.util.UUID
import scala.annotation.experimental
import io.circe.syntax.*
import kreuzberg.miniserver.{
  AssetCandidatePath,
  AssetPaths,
  DeploymentConfig,
  DeploymentType,
  InitRequest,
  MiniServer,
  MiniServerConfig,
  RestrictedAssetCandidatePath
}
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

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

val defaultDeploymentConfig = DeploymentConfig(
  AssetPaths(
    Seq(
      RestrictedAssetCandidatePath(
        deploymentType = Some(DeploymentType.Debug),
        path = AssetCandidatePath("../examples/js/target/client_bundle/client/fast")
      ),
      RestrictedAssetCandidatePath(
        deploymentType = Some(DeploymentType.Production),
        AssetCandidatePath.Resource("examples-opt/")
      ),
      RestrictedAssetCandidatePath(
        deploymentType = Some(DeploymentType.Production),
        AssetCandidatePath.Resource("lib/")
      )
    )
  ),
  extraHtmlHeader = Seq(
    meta(
      charset := "utf-8"
    )
  ),
  htmlRootAttributes = Seq(lang := "en")
)

@experimental
class ServerMainLoom(deploymentConfig: DeploymentConfig = defaultDeploymentConfig) extends App {
  val todoDispatcher: Dispatcher[Id] =
    Dispatcher.makeIdDispatcher[TodoApi[Id]](new TodoServiceLoom: TodoApi[Id]).preRequestFlatMap { request =>
      // Demonstrating adding a pre filter
      // Note: Headers are lower cased
      val id = request.headers.collectFirst {
        case (key, value) if key == "x-client-id" => value
      }
      id match {
        case None => throw SecurityError("Missing client id")
        case _    => request
      }
    }

  // Demonstrating initialization of JS Side with some data of the Server
  val initializer: InitRequest => String = { request =>
    val data   = InitData(code = UUID.randomUUID().toString)
    val asJson = data.asJson.spaces2
    asJson
  }

  val config = MiniServerConfig(
    deploymentConfig,
    api = Some(todoDispatcher),
    init = Some(initializer)
  )

  def run(): Unit = {
    val miniServer = MiniServer(config)
    miniServer.run()
  }
}
