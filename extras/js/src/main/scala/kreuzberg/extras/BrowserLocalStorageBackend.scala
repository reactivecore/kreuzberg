package kreuzberg.extras

class BrowserLocalStorageBackend extends LocalStorage.Backend {
  override def set(key: String, value: String): Unit = {
    org.scalajs.dom.window.localStorage.setItem(key, value)
  }

  override def get(key: String): Option[String] = {
    Option(org.scalajs.dom.window.localStorage.getItem(key))
  }
}
