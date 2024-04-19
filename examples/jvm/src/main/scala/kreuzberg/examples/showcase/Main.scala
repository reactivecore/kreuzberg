package kreuzberg.examples.showcase

import scala.annotation.experimental

@experimental
object Main {
  def main(args: Array[String]): Unit = {
    args.headOption match {
      case None              =>
        println("Expected command")
        System.exit(1)
      case Some("serve")     => ServerMain.main(args.tail)
      case Some("serveLoom") => ServerMainLoom.main(args.tail)
      case Some("export")    => Exporter.main(args.tail)
      case Some(other)       =>
        println("Undefined command")
        System.exit(1)
    }
  }
}
