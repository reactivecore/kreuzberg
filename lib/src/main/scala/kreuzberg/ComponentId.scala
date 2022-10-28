package kreuzberg

case class ComponentId(id: Int) extends AnyVal {
  def inc: ComponentId = copy(id = id + 1)
}
