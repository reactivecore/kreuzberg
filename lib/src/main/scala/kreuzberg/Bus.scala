package kreuzberg

import kreuzberg.util.Stateful

/** IDs for Bus. */
case class BusId(id: Int) extends AnyVal {
  def inc: BusId = copy(id = id + 1)
}

/** Busses are meant for many-2-many event flows. */
case class Bus[T](
    ownerId: ComponentId,
    name: String,
    id: BusId
)

object Bus {
  def make[M](name: String): Stateful[AssemblyState, Bus[M]] = {
    Stateful(_.withBus(name: String))
  }
}
