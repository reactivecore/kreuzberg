package kreuzberg.engine.server

import kreuzberg.*
import kreuzberg.extras.*
import kreuzberg.i18n.{Translations, tr}
import org.scalajs.dom.KeyboardEvent
import kreuzberg.miniserver.*
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.{id as idAttr, *}
import _root_.scalatags.Text.tags2.nav

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

/** Small routed app, used by the HTTP tests and the browser tests of `engine-server-e2e`. */
object TestApp extends SimpleComponentBase {

  /** Engine serving the app; the service repository is created per session. */
  def engine(config: MiniServerConfig): ServerEngine = ServerEngine(
    TestApp,
    () => ServiceRepository.extensible.add(new ServerLocalStorageBackend: LocalStorage.Backend),
    config.copy(translations = Some(translations))
  )

  val translations: TranslationConfig = TranslationConfig(
    resources = Seq("test_i18n/test.default.rctr", "test_i18n/test.de.rctr"),
    defaultLocale = "en"
  )

  /** Created once, like in real apps: its state lives in the session. */
  private lazy val router = MainRouter(
    routes = Vector(
      IndexPage.route,
      CounterPage.route,
      FormPage.route,
      TodoPage.route,
      TimerPage.route,
      LanguagePage.route,
      KeyPage.route,
      UpperPage.route,
      AsyncPage.route,
      lazyRoute,
      failingRoute
    ),
    settings = RouterSettings(titlePrefix = "Test - ")
  )

  override def assemble(using sc: SimpleContext): Html = {
    div(
      idAttr := "app",
      nav(
        ul(
          li(RouterLink(UrlResource("/"), "home").wrap),
          li(RouterLink(UrlResource("/counter"), "counter").wrap),
          li(RouterLink(UrlResource("/form"), "form").wrap),
          li(RouterLink(UrlResource("/todo"), "todo").wrap),
          li(RouterLink(UrlResource("/lazy/x"), "lazy").wrap)
        )
      ),
      router.wrap
    )
  }

  /** Loads after 500ms. */
  val lazyRoute: Route = Route.LazyRoute(
    PathCodec.recursive("/lazy").string.one,
    eagerTitle = _ => "Lazy...",
    fn = id => {
      Future {
        Thread.sleep(500)
        RoutingResult(s"Lazy ${id}", LazyPage(id))
      }(using ExecutionContext.global)
    }
  )

  /** Fails loading, for the error status. */
  val failingRoute: Route = Route.LazyRoute(
    PathCodec.recursive("/failing").string.one,
    eagerTitle = _ => "Failing...",
    fn = _ => Future.failed(new IllegalStateException("Loading failed"))
  )

  case class LazyPage(key: String) extends SimpleComponentBase {
    override def assemble(using sc: SimpleContext): Html = div(idAttr := "page-lazy", s"Loaded ${key}")
  }

  object IndexPage extends SimpleComponentBase with SimpleRouted {
    override def path  = UrlPath.decode("/")
    override def title = "Home"

    override def assemble(using sc: SimpleContext): Html = div(idAttr := "page-home", h1("Home"))
  }

  object CounterPage extends SimpleComponentBase with SimpleRouted {
    override def path  = UrlPath.decode("/counter")
    override def title = "Counter"

    private val counter = Model.create(0)
    private val button  = TestButton("counter-inc", "+1")

    override def assemble(using sc: SimpleContext): Html = {
      val value = subscribe(counter)
      add(button.onClicked.handleAny { counter.update(_ + 1) })
      div(idAttr := "page-counter", h1("Counter"), span(idAttr := "counter-value", value.toString), button.wrap)
    }
  }

  object FormPage extends SimpleComponentBase with SimpleRouted {
    override def path  = UrlPath.decode("/form")
    override def title = "Form"

    private val length = Model.create(0)
    private val box    = TestTextBox("form-input")

    override def assemble(using sc: SimpleContext): Html = {
      val value = subscribe(length)
      add(box.onInput.map(_ => box.text.read().length).handle { l => length.set(l) })
      div(idAttr := "page-form", h1("Form"), box.wrap, span(idAttr := "form-length", value.toString))
    }
  }

  object TodoPage extends SimpleComponentBase with SimpleRouted {
    override def path  = UrlPath.decode("/todo")
    override def title = "Todo"

    private val todos  = Model.create(Vector.empty[String])
    private val box    = TestTextBox("todo-input")
    private val button = TestButton("todo-add", "Add")

    override def assemble(using sc: SimpleContext): Html = {
      val items = subscribe(todos)
      add(button.onClicked.handleAny {
        val text = box.text.read()
        if (text.nonEmpty) {
          todos.update(_ :+ text)
          box.text.set("")
        }
      })
      div(
        idAttr := "page-todo",
        h1("Todo"),
        box.wrap,
        button.wrap,
        ul(idAttr := "todo-list", items.map(li(_)))
      )
    }
  }

  object TimerPage extends SimpleComponentBase with SimpleRouted {
    override def path  = UrlPath.decode("/timer")
    override def title = "Timer"

    private val ticks = Model.create(0)

    override def assemble(using sc: SimpleContext): Html = {
      val value = subscribe(ticks)
      add(EventSource.Timer(100.millis, periodic = true).handleAny { ticks.update(_ + 1) })
      div(idAttr := "page-timer", span(idAttr := "ticks", value.toString))
    }
  }

  object LanguagePage extends SimpleComponentBase with SimpleRouted {
    override def path  = UrlPath.decode("/language")
    override def title = "Language"

    private val count    = Model.create(0)
    private val button   = TestButton("language-inc", "+1")
    private val toGerman = TestButton("to-german", "Deutsch")

    override def assemble(using sc: SimpleContext): Html = {
      val value = subscribe(count)
      add(
        button.onClicked.handleAny { count.update(_ + 1) },
        toGerman.onClicked.handleAny { Translations.switchLocale("de") }
      )
      div(idAttr := "page-language", span(idAttr := "greeting", tr("greeting", value)), button.wrap, toGerman.wrap)
    }
  }

  /** Reacts to Enter only, reading the key on the server. */
  object KeyPage extends SimpleComponentBase with SimpleRouted {
    override def path  = UrlPath.decode("/keys")
    override def title = "Keys"

    private val entered = Model.create("")
    private val box     = TestTextBox("key-input")

    override def assemble(using sc: SimpleContext): Html = {
      val value = subscribe(entered)
      add(
        box.onKeyDown
          .collect { case e: KeyboardEvent if e.key == "Enter" => box.text.read() }
          .handle(text => entered.set(text))
      )
      div(idAttr := "page-keys", box.wrap, span(idAttr := "entered", value))
    }
  }

  /** An input controlled by a model: Enter re-renders it with the upper case text. */
  object UpperPage extends SimpleComponentBase with SimpleRouted {
    override def path  = UrlPath.decode("/upper")
    override def title = "Upper"

    private val text = Model.create("")
    private val box  = ControlledTextBox("upper-input", text)

    override def assemble(using sc: SimpleContext): Html = {
      add(
        box.onKeyDown
          .collect { case e: KeyboardEvent if e.key == "Enter" => box.text.read() }
          .handle(t => text.set(t.toUpperCase))
      )
      div(idAttr := "page-upper", box.wrap)
    }
  }

  /** Re-renders with the model's value. */
  case class ControlledTextBox(elementId: String, model: Model[String]) extends SimpleComponentBase {
    override type DomElement = org.scalajs.dom.html.Input

    val onKeyDown = jsEvent("keydown")
    def text      = jsProperty[String](_.value, (e, v) => e.value = v)

    override def assemble(using sc: SimpleContext): Html = input(idAttr := elementId, value := subscribe(model))
  }

  /** The result arrives 300ms after the click, after the response finished. */
  object AsyncPage extends SimpleComponentBase with SimpleRouted {
    override def path  = UrlPath.decode("/async")
    override def title = "Async"

    private val result = Model.create("none")
    private val button = TestButton("async-start", "Start")

    override def assemble(using sc: SimpleContext): Html = {
      val value = subscribe(result)
      add(button.onClicked.handleAny {
        result.set("running")
        Future {
          Thread.sleep(300)
          "done"
        }(using ExecutionContext.global).foreach(r => result.set(r))
      })
      div(idAttr := "page-async", span(idAttr := "async-result", value), button.wrap)
    }
  }

  case class TestButton(elementId: String, caption: String) extends SimpleComponentBase {
    val onClicked = jsEvent("click")

    override def assemble(using sc: SimpleContext): Html = button(idAttr := elementId, caption)
  }

  case class TestTextBox(elementId: String) extends SimpleComponentBase {
    override type DomElement = org.scalajs.dom.html.Input

    val onInput   = jsEvent("input")
    val onKeyDown = jsEvent("keydown")
    def text      = jsProperty[String](_.value, (e, v) => e.value = v)

    override def assemble(using sc: SimpleContext): Html = input(idAttr := elementId, `type` := "text")
  }
}
