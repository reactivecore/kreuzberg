package kreuzberg.engine.server

import kreuzberg.extras.LocalStorage

import scala.collection.concurrent.TrieMap

/** In-memory [[LocalStorage]] backend, to be created per session (see `ServerEngine.serviceRepositoryFactory`). */
class ServerLocalStorageBackend extends LocalStorage.Backend {
  private val store = TrieMap[String, String]()

  override def set(key: String, value: String): Unit = store.update(key, value)

  override def get(key: String): Option[String] = store.get(key)
}
