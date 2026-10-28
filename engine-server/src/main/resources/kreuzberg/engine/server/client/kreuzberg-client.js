// Kreuzberg server-side rendering client.
//
// The server renders the components and tells this client which events to forward (bindings).
// Events are posted one after another to /krz/event. All updates arrive as numbered operations,
// one JSON object per line, via the stream of this page (/krz/stream), see ClientProtocol.scala.
(() => {
  "use strict";

  const root = document.getElementById("root");
  const pageId = root.getAttribute("data-page");

  /** Listener specs by event name: {key, event, prevent, capture, id | window}. */
  let listenersByEvent = new Map();
  /** Event names with a delegating handler on document / window. */
  const onDocument = new Set();
  const onWindow = new Set();
  /** Set while replacing the focused element, its focus events are not forwarded. */
  let restoringFocus = false;
  /**
   * Set when a newer page of the session was loaded (e.g. in another tab). The next event reloads;
   * not right away, otherwise two open tabs would take the session from each other.
   */
  let stale = false;

  const byId = (id) => root.querySelector(`[data-id="${Number(id)}"]`);

  // ---- Bindings -------------------------------------------------------------------------------

  function applyBindings(op) {
    listenersByEvent = new Map();
    for (const l of op.listeners) {
      if (!listenersByEvent.has(l.event)) listenersByEvent.set(l.event, []);
      listenersByEvent.get(l.event).push(l);
      if (l.window) {
        if (!onWindow.has(l.event)) {
          onWindow.add(l.event);
          window.addEventListener(l.event, onWindowEvent);
        }
      } else if (!onDocument.has(l.event)) {
        onDocument.add(l.event);
        // Capturing, so that non-bubbling events (e.g. focus) are seen as well
        document.addEventListener(l.event, onElementEvent, true);
      }
    }
  }

  // ---- Events ---------------------------------------------------------------------------------

  /**
   * Element listeners, following the DOM: capturing listeners of ancestors (outermost first),
   * then the target and, for bubbling events, its ancestors (innermost first).
   */
  function onElementEvent(e) {
    if (restoringFocus || !(e.target instanceof Element) || !root.contains(e.target)) return;
    const candidates = (listenersByEvent.get(e.type) || []).filter((l) => !l.window);
    if (candidates.length === 0) return;
    const path = []; // components from the target up to the root
    for (let el = e.target.closest("[data-id]"); el && root.contains(el); el = el.parentElement?.closest("[data-id]")) {
      path.push(el);
    }
    const listenersOf = (el, capture) =>
      candidates.filter((l) => l.id === Number(el.getAttribute("data-id")) && !!l.capture === capture);
    const matching = [];
    for (const el of [...path].reverse()) matching.push(...listenersOf(el, true));
    for (const el of path) {
      if (el === e.target || e.bubbles) matching.push(...listenersOf(el, false));
    }
    fire(e, matching, false);
  }

  function onWindowEvent(e) {
    if (restoringFocus) return;
    fire(e, (listenersByEvent.get(e.type) || []).filter((l) => l.window), true);
  }

  function fire(e, listeners, withLocation) {
    if (listeners.length === 0) return;
    if (stale) {
      e.preventDefault();
      return reload();
    }
    if (listeners.some((l) => l.prevent)) e.preventDefault();
    // The location only for window events (e.g. popstate): otherwise it may be outdated,
    // as history changes of earlier events may not be applied yet
    send({ listeners: listeners.map((l) => l.key), event: eventData(e) }, withLocation);
  }

  /** The serializable part of an event. */
  function eventData(e) {
    const data = { type: e.type };
    for (const field of ["key", "code", "button", "clientX", "clientY", "altKey", "ctrlKey", "shiftKey", "metaKey"]) {
      if (e[field] !== undefined) data[field] = e[field];
    }
    return data;
  }

  // ---- Events to the server -------------------------------------------------------------------

  /** Events are posted one after another, each after the server processed the previous one. */
  let queue = Promise.resolve();

  function send(request, withLocation) {
    // Captured now: the state at the time of the event
    request.inputs = inputs();
    request.received = lastSeq;
    if (withLocation) request.location = location.pathname + location.search;
    queue = queue.then(() => post(request)).catch((error) => console.warn("Kreuzberg: event failed", error));
    return queue;
  }

  /** Values of all form inputs, which are components. */
  function inputs() {
    const result = {};
    for (const el of root.querySelectorAll("input[data-id], select[data-id], textarea[data-id]")) {
      result[el.getAttribute("data-id")] = { value: el.value, checked: !!el.checked };
    }
    return result;
  }

  async function post(request) {
    const response = await fetch("/krz/event", {
      method: "POST",
      headers: { "Content-Type": "application/json", "X-Kreuzberg-Page": pageId },
      body: JSON.stringify(request),
    });
    if (response.status === 403) return reload(); // no session cookie
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const result = await response.json();
    if (result.reload) reload(); // session expired or a newer page was loaded
  }

  /** Reload, unless that just happened: e.g. without cookies, the page would reload forever. */
  function reload() {
    const key = "kreuzberg-reload";
    let last = 0;
    try {
      last = Number(sessionStorage.getItem(key) || 0);
      sessionStorage.setItem(key, String(Date.now()));
    } catch (_) {
      // No storage, reload anyway
    }
    if (Date.now() - last < 5000) {
      console.error("Kreuzberg: the session is lost again after reloading, are cookies enabled?");
      stale = true;
      return;
    }
    location.reload();
  }

  // ---- Stream ---------------------------------------------------------------------------------

  /** Number of the last applied operation. */
  let lastSeq = 0;

  /** Receive the operations of this page; reconnects with back-off and resumes after `lastSeq`. */
  async function listen(delay = 500) {
    try {
      const response = await fetch(`/krz/stream?page=${encodeURIComponent(pageId)}&after=${lastSeq}`);
      if (response.status === 404) return reload(); // session expired
      if (response.ok) {
        delay = 500;
        if ((await readOperations(response)) === "stop") return;
      }
    } catch (_) {
      // Connection lost
    }
    setTimeout(() => listen(Math.min(delay * 2, 30000)), delay);
  }

  /** Apply each line of a response; returns "stop", when the page stopped listening. */
  async function readOperations(response) {
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";
    for (;;) {
      const { done, value } = await reader.read();
      if (done) return "end";
      buffer += decoder.decode(value, { stream: true });
      let newline;
      while ((newline = buffer.indexOf("\n")) >= 0) {
        const line = buffer.slice(0, newline);
        buffer = buffer.slice(newline + 1);
        if (line.length === 0) continue;
        const op = JSON.parse(line);
        if (op.seq > 0) {
          if (op.seq <= lastSeq) continue; // already applied before reconnecting
          lastSeq = op.seq;
        }
        if (op.op === "stale") {
          stale = true;
          return "stop";
        }
        apply(op);
      }
    }
  }

  // ---- Operations -----------------------------------------------------------------------------

  function apply(op) {
    try {
      switch (op.op) {
        case "replace":
          replace(op.id, op.html);
          break;
        case "append":
          insert(op.id, "beforeend", op.html);
          break;
        case "prepend":
          insert(op.id, "afterbegin", op.html);
          break;
        case "bindings":
          applyBindings(op);
          break;
        case "set": {
          const el = byId(op.id);
          if (el) el[op.property] = op.value;
          break;
        }
        case "history":
          history[op.push ? "pushState" : "replaceState"]({}, op.title, op.url);
          document.title = op.title;
          break;
        case "title":
          document.title = op.title;
          break;
        case "locale":
          document.cookie = `${op.cookie}=${encodeURIComponent(op.locale)}; path=/; max-age=31536000; SameSite=Lax`;
          location.reload();
          break;
        case "reload":
          reload();
          break;
        case "ping":
          break;
        default:
          console.warn("Kreuzberg: unknown operation", op);
      }
    } catch (error) {
      // Continue with the next operation, e.g. the bindings
      console.warn("Kreuzberg: operation failed", op, error);
    }
  }

  function replace(id, html) {
    const old = byId(id);
    if (!old) return;
    const focus = focusWithin(old);
    const template = document.createElement("template");
    template.innerHTML = html;
    const replacement = template.content.firstElementChild;
    // Removing the focused element may fire blur events
    restoringFocus = true;
    try {
      old.replaceWith(template.content);
    } finally {
      restoringFocus = false;
    }
    if (replacement) restoreFocus(focus);
  }

  function insert(id, position, html) {
    const el = byId(id);
    if (el) el.insertAdjacentHTML(position, html);
  }

  /** Focused component inside an element about to be replaced, with its value and selection. */
  function focusWithin(element) {
    const active = document.activeElement;
    if (!active || !element.contains(active) || !active.hasAttribute("data-id")) return null;
    const focus = {
      id: active.getAttribute("data-id"),
      value: active.value,
      defaultValue: active.defaultValue,
      selection: null,
    };
    try {
      focus.selection = [active.selectionStart, active.selectionEnd];
    } catch (_) {
      // Not every input type has a selection
    }
    return focus;
  }

  /**
   * Focus the re-rendered element again. It keeps what the user typed meanwhile, unless the server
   * rendered a different value (`set` operations follow afterwards anyway).
   */
  function restoreFocus(focus) {
    if (!focus) return;
    const el = byId(focus.id);
    if (!el) return;
    if (focus.defaultValue !== undefined && el.defaultValue === focus.defaultValue) el.value = focus.value;
    restoringFocus = true;
    try {
      el.focus();
    } finally {
      restoringFocus = false;
    }
    if (focus.selection && focus.selection[0] !== null) {
      try {
        el.setSelectionRange(focus.selection[0], focus.selection[1]);
      } catch (_) {
        // Value may have changed its type or length
      }
    }
  }

  // ---- Start ----------------------------------------------------------------------------------

  for (const op of JSON.parse(document.getElementById("krz-init").textContent)) apply(op);
  listen();
})();
