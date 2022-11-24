package kreuzberg

opaque type ComponentId = Int

object ComponentId {

  def apply(id: Int = 1): ComponentId = id

  extension (c: ComponentId) {
    def inc: ComponentId = c + 1

    def id: Int = c
  }
}
