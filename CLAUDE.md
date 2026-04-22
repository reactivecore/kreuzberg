# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What is Kreuzberg

Kreuzberg is a minimalistic Scala 3 web framework for Scala.js with a component-based architecture. Components declare HTML + event bindings + model subscriptions; a separate runtime engine interprets and renders them. It supports ScalaTags and Scala XML for HTML, macro-generated RPC between client/server, and macro-generated forms from annotated case classes.

## Build Commands

Requires JVM >= 21. Build tool is sbt.

```bash
sbt compile                          # Compile all JVM projects
sbt test                             # Run all tests
sbt extrasJVM/test                   # Run tests for a specific subproject
sbt "extrasJVM/Test/testOnly kreuzberg.extras.forms.GeneratorTest"  # Single test class
sbt examplesJS/fastOptJS             # Compile JS (dev mode)
sbt runner/run                       # Start example server (debug)
~sbt runner/reStart                  # Watch & restart on changes
sbt lint                             # Format (scalafmt) + lint (scalafix)
sbt lintCheck                        # Check formatting without fixing
```

Cross-compiled subprojects use platform suffixes: `extrasJVM`, `extrasJS`, `extrasNative`.

## Project Structure

| Subproject | Platforms | Purpose |
|---|---|---|
| `lib` | JS/JVM/Native | Core: Component, Model, Channel, EventBinding, Assembly, Html |
| `engine-naive` | JS only | Mutable DOM rendering engine |
| `scalatags` | JS/JVM/Native | ScalaTags HTML integration |
| `xml` | JS/JVM/Native | Scala XML HTML integration |
| `extras` | JS/JVM/Native | Router, Forms (macro-generated), Tables |
| `rpc` | JS/JVM/Native | Macro-generated client/server RPC (experimental, needs `@experimental`) |
| `miniserver` | JVM only | Tapir/Netty HTTP server |
| `testCore` | JS/JVM/Native | Shared test base (ScalaTest FlatSpec) |
| `js-dom-mock` | JVM/Native | Mocks `org.scalajs.dom` types for cross-compilation |
| `examples` | JS/JVM | Showcase application |
| `runner` | JVM | Wraps examples + debug mode |

Dependency chain: `testCore` <- `js-dom-mock` <- `lib` <- `scalatags`/`xml` <- `extras` <- `examples`

## Architecture

**Core model** (`lib`): `Component` has an `assemble` method returning `Assembly` (Html + EventBindings + Subscriptions). `Model[T]` is mutable subscribable state. `Channel[T]` is n:m event communication without current value. Components re-render when subscribed Models change. Events are the only way to mutate Models.

**SimpleComponentBase**: The most common way to implement components. Uses imperative `SimpleContext` DSL to register handlers/subscriptions during `assemble`. Contrast with lower-level `ComponentBase` which is purely functional.

**Forms system** (`extras/forms`): Annotate a case class with `@UseField` / `@UseValidator`, then `Generator.generate[T]` macro-produces a `Form[T]` with fields, codecs, and validators. `FormComponent` renders the form with input/select elements. `FormField.options` triggers `<select>` rendering.

**RPC** (`rpc`): Define a trait, implement on server. `Dispatcher.makeDispatcher[A]` and `Stub.makeStub[A]` are macro-generated. Uses Circe JSON.

## Code Conventions

- **Scala 3.7.4**, all warnings are errors (`-Wconf:any:e`)
- **Formatting**: scalafmt with `maxColumn = 120`, `align.preset = most`, scala3 dialect
- **Linting**: scalafix with `DisableSyntax` (no vars, no finalize), `RemoveUnused`, `RedundantSyntax`
- **Testing**: ScalaTest FlatSpec via `TestBase` from `testCore`. Pattern: `it should "desc" in { ... shouldBe ... }`
- **Cross-compilation**: Shared code goes in `shared/src/main/scala`. JS-only DOM types need mocks in `js-dom-mock` for JVM/Native compilation (e.g., `org.scalajs.dom.html.Input`, `Select`)
- **File ordering**: Within a file, place referenced definitions *below* the code that references them. The primary type comes first; helper/support types it uses appear after it.
