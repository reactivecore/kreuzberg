package kreuzberg

private[kreuzberg] class SimpleThreadLocal[T](initial: T) {
  private val _val    = new ThreadLocal[T] {
    override def initialValue(): T = {
      initial
    }
  }
  inline def get(): T = _val.get()

  inline def set(value: T): Unit = _val.set(value)

  /** Use a specific instance for running a function. */
  def withInstance[R](value: T)(f: => R): R = {
    val current = get()
    try {
      set(value)
      f
    } finally {
      set(current)
    }
  }
}
