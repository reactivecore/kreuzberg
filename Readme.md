# Kreuzberg

Kreuzberg is a minimalistic library designed to evaluate the use of Scala JS in web applications through a simple component architecture.

It is built upon:

- [Scala JS](https://www.scala-js.org/)
- [Scala DOM](https://scala-js.github.io/scala-js-dom/)
- [ScalaTags](https://github.com/com-lihaoyi/scalatags) (optional)
- [Circe](https://circe.github.io/circe/) (optional)
- [ZIO](https://zio.dev/) (optional, example server)

Please note, this is beta software and some parts may have been hastily written.

# Design Goals and Features

The aim of Kreuzberg is to simplify the use of Scala in web applications. Its key features include:

- Scala 3
- Full type safety on both the client and server sides
- Declarative components
- Separation of the runtime engine from the component implementation. Each component declares how it functions, while the act of combining and running components is performed by an engine.
- Automatically generated API calls between client and server. One trait is defined and implemented on the server. Stubs (on the client side) and dispatchers (on the server side) are macro-generated.
 
  **Note:** This depends on experimental Scala features.
- Deriving HTML Forms from Case-Classes, including Validation.  
- HTML technique agnosticism. There is support for Scalatags and Scala XML, and more implementations can be added.
- Theoretically, it should be possible to implement a server-side engine, but this has not yet been done.

Kreuzberg prioritizes ease-of-use over optimal performance or full functional programming.

# Building Blocks

- The most important element is a `Component`. Each component has an ID and is integrated into the HTML DOM.
- Each component features an `assemble` method, which is called when it is about to be rendered or when its associated model changes. The assemble method returns the HTML code, event bindings, and subscribed models.
- A `Model` is a mutable element. It has an ID and a default value (if it does not exist yet). Models can be subscribed to by components, meaning they will be re-rendered if the model changes.
  Models can only change on Events.
- An `EventBinding` Components can declare Event Bindings. They are the only way to change model, which triggers component re-rendering.
- `Channels` enable n:m communication. Any event can trigger a channel, and any component can register events on them. Unlike `Model`, `Channels` do not carry a current value, but each invocation carries a value.
- A `State` encapsulates a component's viewing state (e.g., the text entered into a form field). States can be accessed during event handling.

`Component`s, except for the ID mechanism, are functional. They return data structures, and the interpretation is handled by an engine.

For simplification, there is a `SimpleComponentBase`, which makes it easier to implement a `Component`. Note, however, that it maintains an inner state to facilitate the implementation of its `assemble` method.

# Structure

- `lib`: The main Kreuzberg library, required for implementing components.
- `scalatags`: Support for ScalaTags.
- `xml`: Support for Scala XML. Adds at least 50kb (13kb compressed).
- `extras`: Contains various components, including a simple router.
- `rpc`: An experimental RPC library for making calls between JavaScript and JVM. Needs `@experimental`-Annotation
- `examples`: Sample applications.
- `miniserver`: A simple ZIO-ZHTTP-based server for starting the example application.
- `engine-common`: Contains common engine code.
- `engine-naive`: Contains the naive rendering engine.
- `runner`: Wraps the naive engine with examples.
 
# How to

- Compile JavaScript code, in SBT

  ```
  examplesJS/fastOptJS
  ```
- Start the Examples (naive engine)
  ```
  runner/run serve
  ```

- Watching for changes

  - ScalaJS Only: `~examplesJS/fastOptJS`
  - Full: `~runner/reStart`

# Notes

- Scala Native is not tested
