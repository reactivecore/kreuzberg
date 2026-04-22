package kreuzberg.engine.naive

import java.util.concurrent.atomic.AtomicReference
import kreuzberg.*
import kreuzberg.testcore.TestBase

class UpdatePathTest extends TestBase {

  case class Record(name: String, counter: Int)

  case class NameView(model: Model[Record]) extends SimpleComponentBase {
    def assemble(using sc: SimpleContext): Html = {
      val name = model.map(_.name).subscribe()
      SimpleHtml("div", children = Vector(SimpleHtmlNode.Text(s"Name: $name")))
    }
  }

  /**
   * Build a tree with a KreuzbergContext whose mvp reads a mutable ModelValues. The `body` receives the freshly built
   * Model, the TreeNode, and a function to update the current ModelValues.
   */
  private def withTree[T](build: Model[Record] => Component, initial: Record)(
      body: (Model[Record], TreeNode, ModelValues => Unit) => T
  ): T = {
    IdentifierFactory.withFresh {
      val model  = Model.create(initial)
      val values = new AtomicReference(ModelValues().withModelValue(model.id, initial))
      val ctx    = new KreuzbergContext.Compound(
        mvp = new ModelValueProvider {
          override def modelValue[M](m: Model[M]): M = values.get().value(m)
        },
        sr = ServiceRepository.empty,
        changer = Changer.empty
      )
      KreuzbergContext.threadLocal.withInstance(ctx) {
        val tree = Assembler.tree(build(model))
        body(model, tree, (update: ModelValues) => values.set(update))
      }
    }
  }

  it should "skip re-render when mapped subscribed value is unchanged" in {
    withTree(m => NameView(m), Record("A", 0)) { (model, tree, setValues) =>
      // Capture before-state, then mutate counter only (name unchanged)
      val before = ModelValues().withModelValue(model.id, Record("A", 0)).toModelValueProvider
      setValues(ModelValues().withModelValue(model.id, Record("A", 42)))

      val path = UpdatePath.build(tree, Set(model.id), before)
      path.changes shouldBe empty
      path.tree.subscriptions.map(_.lastValue) shouldBe Vector("A")
    }
  }

  case class OptOutView(model: Model[Record]) extends SimpleComponentBase {
    def assemble(using sc: SimpleContext): Html = {
      val name = model.map(_.name).subscribe()
      SimpleHtml("div", children = Vector(SimpleHtmlNode.Text(s"Name: $name")))
    }

    override def update(before: ModelValueProvider): UpdateResult = UpdateResult.Unchanged
  }

  it should "preserve the existing TreeNode when update returns Unchanged" in {
    withTree(m => OptOutView(m), Record("A", 0)) { (model, tree, setValues) =>
      val before = ModelValues().withModelValue(model.id, Record("A", 0)).toModelValueProvider
      // Change the mapped value so the value-equality skip does NOT fire — only Unchanged should save us.
      setValues(ModelValues().withModelValue(model.id, Record("B", 0)))

      val path = UpdatePath.build(tree, Set(model.id), before)
      path.changes shouldBe empty
      path.tree.id shouldBe tree.id
      path.tree.html shouldBe tree.html
    }
  }

  it should "re-render when mapped subscribed value changes" in {
    withTree(m => NameView(m), Record("A", 0)) { (model, tree, setValues) =>
      val before = ModelValues().withModelValue(model.id, Record("A", 0)).toModelValueProvider
      setValues(ModelValues().withModelValue(model.id, Record("B", 0)))

      val path = UpdatePath.build(tree, Set(model.id), before)
      path.changes should have size 1
      path.changes.head shouldBe a[UpdatePath.Change.Rerender]
      path.tree.subscriptions.map(_.lastValue) shouldBe Vector("B")
    }
  }
}
