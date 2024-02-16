package kreuzberg.rpc
import zio.Task

import scala.annotation.experimental
import scala.quoted.*
import scala.concurrent.Future

/** Macro generated Stubs forwarding calls to [[CallingBackend]] */
object Stub {

  /** Returns the name for a service. */
  inline def serviceName[T] = ${
    serviceNameMacro[T]
  }

  def serviceNameMacro[T](using Type[T], Quotes): Expr[String] = {
    val analyzer = new TraitAnalyzer()
    val analyzed = analyzer.analyze[T]
    Expr(analyzed.name)
  }

  /** Generates a Stub for T */
  @experimental
  inline def makeStub[T](backend: CallingBackend[Future, String]): T = ${
    makeStubMacro[Future, String, T]('backend)
  }

  @experimental
  inline def makeZioStub[T](backend: CallingBackend[Task, String]): T = ${
    makeStubMacro[Task, String, T]('backend)
  }

  // Note: Can't be private, see  https://github.com/lampepfl/dotty/issues/16091
  @experimental
  def makeStubMacro[F[_], T, A](
      backend: Expr[CallingBackend[F, T]]
  )(using Type[A], Type[F], Type[T], Quotes): Expr[A] = {

    val analyzer = new TraitAnalyzer()
    import analyzer.quotes.reflect.*

    val parents = List(TypeTree.of[Object], TypeTree.of[A])

    val analyze  = analyzer.analyze[A]
    val methods  = analyze.methods
    val implName = analyze.name + "_impl"

    val effect = Expr.summon[EffectSupport[F]].getOrElse {
      throw new IllegalArgumentException(
        "Could not find Effect for F (if you are using Future, there must be an ExecutionContext present)"
      )
    }

    val mc = Expr.summon[MessageCodec[T]].getOrElse {
      throw new IllegalArgumentException("Could not find MessageCodec for T")
    }

    def decls(cls: Symbol): List[Symbol] = {
      methods.map { method =>
        // println(s"Generating ${method.name}")

        type R
        given Type[R] = method.returnType.typeArgs.head.asType.asInstanceOf[Type[R]]
        type Outer = F[R]
        val outerTyper = TypeRepr.of[Outer]

        Symbol.newMethod(
          parent = cls,
          name = method.name,
          tpe = MethodType(method.paramNames)(_ => method.paramTypes, _ => outerTyper)
        )
      }
    }

    val cls = Symbol.newClass(Symbol.spliceOwner, implName, parents = parents.map(_.tpe), decls, selfType = None)

    def makeDecode(method: analyzer.Method)(inner: Expr[F[T]]): Term = {
      type R
      given Type[R]   = method.returnType.typeArgs.head.asType.asInstanceOf[Type[R]]
      val returnCodec = Expr.summon[Codec[R, T]].getOrElse {
        throw new IllegalArgumentException("Could not find codec for type R" + method.returnType)
      }
      val x           = '{ ${ effect }.decodeResult[R, T](${ inner })(${ returnCodec }) }
      x.asTerm
    }

    def encodeArg(dtype: TypeRepr, arg: Tree): Expr[T] = {
      type X
      given Type[X] = dtype.asType.asInstanceOf[Type[X]]
      val codec     = Expr.summon[Codec[X, T]].getOrElse {
        throw new IllegalArgumentException("Could not find codec for type X" + dtype)
      }
      '{ ${ codec }.encode(${ arg.asExprOf[X] }) }
    }

    def encodeNamedArgs(names: List[String], dtypes: List[TypeRepr], args: List[Tree]): Expr[T] = {
      val argExpressions: List[Expr[(String, T)]] = names.zip(dtypes).zip(args).map { case ((name, dtype), arg) =>
        '{ ${ Expr(name) } -> ${ encodeArg(dtype, arg) } }
      }

      val varArgs = scala.quoted.Varargs(argExpressions)

      '{ $mc.combine($varArgs: _*) }
    }

    def multiArgImplementation(method: analyzer.Method, args: List[List[Tree]]): Term = {
      val encodedArgs = encodeNamedArgs(method.paramNames, method.paramTypes, args.head)
      makeDecode(method) {
        '{ $backend.call(${ Expr(analyze.apiName) }, ${ Expr(method.name) }, $encodedArgs) }
      }
    }

    val methodDefinitions = methods.map { method =>
      val declaration = cls.declaredMethod(method.name).head
      DefDef(
        declaration,
        argss =>
          Some(
            multiArgImplementation(method, argss)
          )
      )
    }

    val clsDef = ClassDef(cls, parents, body = methodDefinitions)
    val newCls =
      Typed(Apply(Select(New(TypeIdent(cls)), cls.primaryConstructor), Nil), TypeTree.of[A])
    val result = Block(List(clsDef), newCls).asExprOf[A]
    // println(s"RESULT: ${result.show}")
    result
  }
}
