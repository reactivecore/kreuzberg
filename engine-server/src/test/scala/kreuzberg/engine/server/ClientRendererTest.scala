package kreuzberg.engine.server

import io.circe.Json
import kreuzberg.*
import kreuzberg.extras.UrlResource
import kreuzberg.miniserver.DeploymentConfig
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*
import kreuzberg.testcore.TestBase

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.duration.DurationInt

class ClientRendererTest extends TestBase {

  /** Opens a page of the component and returns its session. */
  private def open(component: Component): Session = {
    val manager = SessionManager(component, () => ServiceRepository.empty, 1.hour)
    try {
      manager.openPage(None, UrlResource("/"))(identity)._1
    } finally {
      manager.close()
    }
  }

  private def render(component: Component): String = {
    ClientRenderer.renderPage(open(component), DeploymentConfig(), "/client.js", Nil, lang = None)
  }

  /** The bindings operation embedded into a page. */
  private def bindings(component: Component): Json = {
    TestServer.initOps(render(component)).asArray.get.find(_.hcursor.get[String]("op").contains("bindings")).get
  }

  private def listeners(component: Component): Vector[Json] =
    bindings(component).hcursor.get[Vector[Json]]("listeners").toOption.get

  private class Clickable(preventDefault: Boolean = false, capture: Boolean = false) extends SimpleComponentBase {
    val clicked = jsEvent("click", preventDefault, capture)

    override def assemble(using sc: SimpleContext): Html = button("click")
  }

  "renderPage" should "include the client, the components and the bindings" in {
    val component = new SimpleComponentBase {
      override def assemble(using sc: SimpleContext): Html = div("hello")
    }
    val session   = open(component)
    val page      = ClientRenderer.renderPage(session, DeploymentConfig(), "/client.js", Nil, lang = None)
    page should startWith("<!DOCTYPE html>")
    page should include("""<script defer="defer" src="/client.js"></script>""")
    // Components are rendered as they are, without any attributes for the client
    page should include(s"""><div data-id="${component.id.value}">hello</div></div>""")
    page should include(s"""<div id="root" data-page="${session.page.id}">""")
    page should include("""<script type="application/json" id="krz-init">[{"op":"bindings"""")
  }

  it should "escape HTML in the embedded operations" in {
    val script = ClientProtocol.initScript(Seq(ClientProtocol.Op.Title("</script><!--<script>")))
    script should not include "<"
    io.circe.parser.parse(script).toOption.get.noSpaces should include("</script><!--<script>")
  }

  "bindings" should "merge handlers of the same element and event into one listener" in {
    val child     = Clickable(preventDefault = true)
    val other     = Clickable()
    val component = new SimpleComponentBase {
      override def assemble(using sc: SimpleContext): Html = {
        addHandlerAny(child.clicked)(())
        addHandlerAny(other.clicked)(())
        add(child.clicked.handleAny(()))
        div(child.wrap, other.wrap)
      }
    }
    listeners(component).map(_.noSpaces).toSet shouldBe Set(
      s"""{"key":"e:${child.id.value}:click","event":"click","prevent":true,"capture":false,"id":${child.id.value},"window":false}""",
      s"""{"key":"e:${other.id.value}:click","event":"click","prevent":false,"capture":false,"id":${other.id.value},"window":false}"""
    )
  }

  it should "key window listeners by owner" in {
    val component = new SimpleComponentBase {
      override def assemble(using sc: SimpleContext): Html = {
        addHandlerAny(windowEvent("resize"))(())
        div()
      }
    }
    listeners(component).map(_.noSpaces) shouldBe Vector(
      s"""{"key":"w:${component.id.value}:resize","event":"resize","prevent":false,"capture":false,"id":null,"window":true}"""
    )
  }

  it should "apply transformers like the browser engine" in {
    val received  = AtomicReference(Vector.empty[Any])
    val button    = Clickable()
    val channel   = Channel.create[Int]()
    val component = new SimpleComponentBase {
      override def assemble(using sc: SimpleContext): Html = {
        add(
          button.clicked.map(_ => "mapped").handle(v => received.updateAndGet(_ :+ v)),
          channel.collect { case v if v > 5 => v * 2 }.handle(v => received.updateAndGet(_ :+ v))
        )
        div(button.wrap)
      }
    }
    val session   = open(component)
    session.withLock {
      session.run {
        val listener = session.bindings.listeners(Bindings.ListenerKey.Element(button.id, "click"))
        listener.handlers.foreach(_(null))
        channel(3)
        channel(10)
      }
    }
    received.get() shouldBe Vector("mapped", 20)
  }
}
