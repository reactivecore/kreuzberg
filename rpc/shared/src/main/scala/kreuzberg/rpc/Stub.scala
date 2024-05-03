package kreuzberg.rpc
import io.circe.{Decoder, Encoder, Json}

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
  inline def makeStub[T](backend: CallingBackend[Future]): T = ${
    makeStubMacro[Future, T]('backend)
  }

  @experimental
  inline def makeIdStub[T](backend: CallingBackend[Id]): T = ${
    makeStubMacro[Id, T]('backend)
  }

  // Note: Can't be private, see  https://github.com/lampepfl/dotty/issues/16091
  @experimental
  def makeStubMacro[F[_], A](
      backend: Expr[CallingBackend[F]]
  )(using Type[A], Type[F], Quotes): Expr[A] = {

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

    def makeDecode(method: analyzer.Method)(inner: Expr[F[Response]]): Term = {
      type R
      given Type[R]     = method.returnType.typeArgs.head.asType.asInstanceOf[Type[R]]
      val returnDecoder = Expr.summon[Decoder[R]].getOrElse {
        throw new IllegalArgumentException("Could not find codec for type R" + method.returnType)
      }
      val x             = '{ ${ effect }.decodeResponse[R](${ inner })(${ returnDecoder }) }
      x.asTerm
    }

    def encodeArg(dtype: TypeRepr, arg: Tree): Expr[Json] = {
      type X
      given Type[X]  = dtype.asType.asInstanceOf[Type[X]]
      val argEncoder = Expr.summon[Encoder[X]].getOrElse {
        throw new IllegalArgumentException("Could not find encoder for type X" + dtype)
      }
      '{ ${ argEncoder }.apply(${ arg.asExprOf[X] }) }
    }

    def encodeNamedArgs(names: List[String], dtypes: List[TypeRepr], args: List[Tree]): Expr[Json] = {
      val argExpressions: List[Expr[(String, Json)]] = names.zip(dtypes).zip(args).map { case ((name, dtype), arg) =>
        '{ ${ Expr(name) } -> ${ encodeArg(dtype, arg) } }
      }

      val varArgs = scala.quoted.Varargs(argExpressions)

      '{ MessageCodec.combine($varArgs: _*) }
    }

    def multiArgImplementation(method: analyzer.Method, args: List[List[Tree]]): Term = {
      val encodedArgs = encodeNamedArgs(method.paramNames, method.paramTypes, args.head)
      makeDecode(method) {
        '{ $backend.call(${ Expr(analyze.apiName) }, ${ Expr(method.name) }, Request($encodedArgs)) }
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
