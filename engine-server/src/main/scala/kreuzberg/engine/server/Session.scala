package kreuzberg.engine.server

import io.circe.{Json, JsonObject}
import kreuzberg.*
import kreuzberg.engine.{ModelValues, UpdatePath}
import kreuzberg.extras.{BrowserRouting, Router, UrlResource}
import kreuzberg.i18n.{Translations, TranslationsPlatform}

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.{ScheduledExecutorService, ScheduledFuture, TimeUnit}
import scala.collection.mutable
import scala.util.control.NonFatal

/**
 * State of one browser session: the component tree, model values and event bindings.
 *
 * All mutable state must only be accessed within [[withLock]]. Async callbacks (e.g. a `Future` completing on the
 * component's execution context) only enqueue into the [[changer]], which is thread safe; they are processed by the
 * page's stream (or the next event). Operations for the browser are published to the current [[Page]].
 */
private[server] class Session(
    val id: String,
    serviceRepository: ServiceRepository,
    scheduler: ScheduledExecutorService
) {
  // Fair, so that concurrent requests are processed in order of arrival
  private val lock = ReentrantLock(true)

  @volatile private var lastAccess: Long = System.currentTimeMillis() // scalafix:ok

  /** Signalled on new pending changes and published operations, see [[awaitActivity]]. */
  private val activityLock = ReentrantLock()
  private val activity     = activityLock.newCondition()

  /** The current page load; older pages (e.g. of another tab) are reloaded on their next event. */
  @volatile var page: Page = Page() // scalafix:ok

  /** Title the browser shows, as far as sent. */
  var titleShown: String = "" // scalafix:ok

  var tree: TreeNode           = TreeNode.empty // scalafix:ok
  var modelValues: ModelValues = ModelValues()  // scalafix:ok
  var bindings: Bindings       = Bindings()     // scalafix:ok

  /** Values of form inputs (`value`, `checked`) by component, as sent with the last event. */
  var inputs: Map[Identifier, JsonObject] = Map.empty // scalafix:ok

  /** DOM properties set on the server (e.g. clearing an input), not yet sent to the browser. */
  var pendingWrites: Vector[Session.PropertyWrite] = Vector.empty // scalafix:ok

  var currentUrl: UrlResource = UrlResource() // scalafix:ok
  var currentTitle: String    = ""            // scalafix:ok

  /** Translations of the session's locale. */
  var translations: Translations = Translations.empty // scalafix:ok

  /** Locale requested by `Translations.switchLocale`, not yet sent to the browser. */
  var pendingLocale: Option[String] = None // scalafix:ok

  /** History changes (pushState/replaceState), not yet sent to the browser. */
  var pendingUrlUpdates: Vector[Session.UrlUpdate] = Vector.empty // scalafix:ok

  val changer: ServerChanger = ServerChanger(this)

  private val context = KreuzbergContext.Compound(
    new ModelValueProvider {
      // Live view, re-assembled components must see the newest values.
      override def modelValue[M](model: Model[M]): M = modelValues.value(model)
    },
    serviceRepository,
    changer
  )

  private val routingBackend = ServerBrowserRoutingBackend(this)

  private val translationsBackend = new TranslationsPlatform.Backend {
    override def translations: Translations         = Session.this.translations
    override def switchLocale(locale: String): Unit = pendingLocale = Some(locale)
  }

  /** Execute a block under the session lock. */
  def withLock[T](f: => T): T = {
    lock.lock()
    try {
      lastAccess = System.currentTimeMillis()
      f
    } finally {
      lock.unlock()
    }
  }

  def isExpired(expiryMillis: Long): Boolean = System.currentTimeMillis() - lastAccess > expiryMillis

  /** Keep the session alive, e.g. while a page is open. */
  def touch(): Unit = lastAccess = System.currentTimeMillis()

  /** Publish operations to the current page. */
  def publish(ops: Seq[ClientProtocol.Op]): Unit = {
    if (ops.nonEmpty) {
      page.append(ops)
      signalActivity()
    }
  }

  /** Wake up waiting streams; called after state changes, which `ready` of [[awaitActivity]] checks. */
  def signalActivity(): Unit = {
    activityLock.lock()
    try activity.signalAll()
    finally activityLock.unlock()
  }

  /**
   * Wait up to `timeoutMillis` until `ready` holds. It is re-checked on each [[signalActivity]].
   * @return
   *   the final value of `ready`.
   */
  def awaitActivity(timeoutMillis: Long)(ready: => Boolean): Boolean = {
    activityLock.lock()
    try {
      var nanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis) // scalafix:ok
      while (!ready && nanos > 0) {
        nanos = activity.awaitNanos(nanos)
      }
      ready
    } finally {
      activityLock.unlock()
    }
  }

  /** Run code within the Kreuzberg context of this session. */
  def run[T](f: => T): T = BrowserRouting.withBackend(routingBackend) {
    TranslationsPlatform.withBackend(translationsBackend) {
      context.use(f)
    }
  }

  /** Fire the `popstate` listeners of the window, as on history navigation. */
  def firePopState(): Unit = {
    val event = MockEvent(JsonObject("type" -> Json.fromString("popstate")))
    run {
      bindings.listeners.foreach {
        case (Bindings.ListenerKey.Window(_, "popstate"), listener) => listener.handlers.foreach(_(event))
        case _                                                      => ()
      }
    }
  }

  /** (Re)build the whole tree, e.g. on page load. Model values are kept. */
  def assemble(component: Component): Unit = {
    tree = run(Assembler.tree(component))
    treeChanged(restart = tree.iterator.map(_.id).toSet)
  }

  /**
   * After the tree changed: collect bindings and (re)start timers.
   *
   * @param restart
   *   re-rendered components, whose timers start again
   */
  private def treeChanged(restart: Set[Identifier]): Unit = {
    bindings = Bindings.collect(tree)
    updateTimers(restart)
  }

  /** Scheduled timers of the bindings. */
  private val timers = mutable.Map[Bindings.TimerKey, ScheduledFuture[?]]()

  private def updateTimers(restart: Set[Identifier]): Unit = {
    val keys = bindings.timers.keySet
    timers.filterInPlace { (key, future) =>
      val keep = keys.contains(key) && !restart.contains(key.owner)
      if (!keep) future.cancel(false)
      keep
    }
    keys.filterNot(timers.contains).foreach { key => timers(key) = schedule(key) }
  }

  /**
   * A tick queues the timer's handlers, like async work; the page's stream processes them. Ticks are coalesced: at most
   * one per timer waits to be processed.
   */
  private def schedule(key: Bindings.TimerKey): ScheduledFuture[?] = {
    val waiting        = AtomicBoolean(false)
    val tick: Runnable = () => {
      if (waiting.compareAndSet(false, true)) {
        changer.call { () =>
          waiting.set(false)
          bindings.timers.get(key).foreach(_.foreach(_()))
        }
      }
    }
    val millis         = key.duration.toMillis.max(1L)
    if (key.periodic) scheduler.scheduleAtFixedRate(tick, millis, millis, TimeUnit.MILLISECONDS)
    else scheduler.schedule(tick, millis, TimeUnit.MILLISECONDS)
  }

  /** Stop all timers, when the session is removed. */
  def close(): Unit = withLock {
    timers.values.foreach(_.cancel(false))
    timers.clear()
  }

  /**
   * Process pending changes until the tree is stable.
   *
   * Async work is not awaited: its results arrive later and are processed by the page's stream.
   *
   * @param forPage
   *   for page loads, which can only be delivered complete: also wait for async work, as long as it arrives within
   *   [[Session.QuietPeriodMillis]] or the router is loading, up to [[Session.MaxWaitMillis]]
   * @param onChanges
   *   called after each processing step with its changes, so that they can be sent right away.
   */
  def settle(forPage: Boolean = false)(onChanges: Seq[UpdatePath.Change] => Unit = _ => ()): Unit = {
    val deadline = System.currentTimeMillis() + Session.MaxWaitMillis
    onChanges(drain())
    var waiting  = true // scalafix:ok
    var rounds   = 1    // scalafix:ok
    // Without waiting (events), only a few rounds: e.g. a fast timer must not keep the session busy,
    // remaining work is processed by the page's stream, which sends in between.
    while (waiting && (forPage || rounds < Session.MaxRounds)) {
      rounds += 1
      val remaining = deadline - System.currentTimeMillis()
      val timeout   =
        if (!forPage) 0L else if (isRouterLoading) remaining else math.min(Session.QuietPeriodMillis, remaining)
      // Re-assembled components may have queued further changes (e.g. the router)
      if (changer.hasPending || (timeout > 0 && awaitActivity(timeout)(changer.hasPending))) {
        if (System.currentTimeMillis() < deadline) onChanges(drain()) else waiting = false
      } else {
        waiting = false
      }
    }
    collectGarbage()
  }

  /**
   * Drop values of models which are not subscribed anymore, like the browser engine. Only on a settled tree: while
   * settling (e.g. the router before showing the page), the subscriptions are incomplete.
   */
  private def collectGarbage(): Unit = {
    // The engine itself reads them, see isRouterLoading and statusCode
    val retained = tree.allSubscriptions.map(_._1).toSet + Router.loading.id + Router.statusCode.id
    modelValues = modelValues.copy(modelValues = modelValues.modelValues.view.filterKeys(retained.contains).toMap)
  }

  /** Process the pending queue once and update the tree. */
  private def drain(): Seq[UpdatePath.Change] = {
    val before        = modelValues.toModelValueProvider
    val changedModels = run(changer.processPending())
    if (changedModels.isEmpty) {
      Nil
    } else {
      val path = run(UpdatePath.build(tree, changedModels, before))
      tree = path.tree
      treeChanged(restart = path.changes.flatMap(_.nodes).flatMap(_.iterator.map(_.id)).toSet)
      path.changes
    }
  }

  /** HTTP status of the current page, as decided by the router. */
  def statusCode: Int = modelValues.value(Router.statusCode)

  /** Lazy routes signal outstanding async work via [[Router.loading]]. */
  private def isRouterLoading: Boolean = {
    try {
      modelValues.value(Router.loading)
    } catch {
      case NonFatal(_) => false
    }
  }

  def takePendingWrites(): Vector[Session.PropertyWrite] = {
    val result = pendingWrites
    pendingWrites = Vector.empty
    result
  }

  def takePendingLocale(): Option[String] = {
    val result = pendingLocale
    pendingLocale = None
    result
  }

  def takePendingUrlUpdates(): Vector[Session.UrlUpdate] = {
    val result = pendingUrlUpdates
    pendingUrlUpdates = Vector.empty
    result
  }
}

private[server] object Session {

  /** How long to wait for further async work, if nothing signals that more is to come. */
  val QuietPeriodMillis: Long = 50L

  /** Processing rounds of an event, see [[settle]]. */
  val MaxRounds: Int = 10

  /** Maximum time a request waits for async work. */
  val MaxWaitMillis: Long = 10_000L

  case class UrlUpdate(title: String, url: String, push: Boolean)

  case class PropertyWrite(id: Identifier, property: String, value: Json)
}
