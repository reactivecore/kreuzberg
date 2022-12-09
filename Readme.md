# Kreuzberg

Minimalistic Library for evaluating the use of Scala JS for web applications with a simple component architecture.

Based upon:

- [Scala JS](https://www.scala-js.org/)
- [Scala DOM](https://scala-js.github.io/scala-js-dom/)
- [ScalaTags](https://github.com/com-lihaoyi/scalatags)

Note: this is Alpha Software with no tests.

# Structure

- `lib` the main Kreuzberg Library
- `extras` some components, especially a simple router.
- `xml` Scala XML Support. Will add at least 50kb (13kb compressed)
- `scalatags` ScalaTags Support
- `examples` sample application
- `miniserver` simple ZIO-ZHTTP based server for starting the example application.


# How to

- Compile JavaScript code, in SBT
  
  ```
  examplesJS/fastOptJS
  ```
- Start Mini Server, in SBT
  ```
  miniserver/run
  ```
- Watching for changes
  
  - ScalaJS Only: `~examplesJS/fastOptJS`
  - Miniserver Only: `~miniserver/reStart serve`
  - Full: `~runner/reStart`
