package kreuzberg.util

class SimpleThreadLocal[T](initial: T) {
  private val _val = new ThreadLocal[T] {
    override def initialValue(): T = {
      initial
    }
  }
  def get(): T     = _val.get()

  def set(value: T): Unit = _val.set(value)
}
