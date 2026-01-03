package kreuzberg.rpc

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
        val methodType = generateMethodType(method)

        Symbol.newMethod(
          parent = cls,
          name = method.name,
          tpe = methodType
        )
      }
    }

    def generateMethodType(method: analyzer.Method): MethodType = {
      type R
      given Type[R] = method.returnType.typeArgs.head.asType.asInstanceOf[Type[R]]

      type Outer = F[R]
      val outerTyper = TypeRepr.of[Outer]

      if (method.parameters.isEmpty) {
        MethodType(Nil)(_ => Nil, _ => outerTyper)
      } else {
        val last = method.parameters.last

        val base   = MethodType(last.methodTypeKind)(last.parameters.map(_.name))(
          _ => last.parameters.map(_.parameterType),
          _ => outerTyper
        )
        val result = method.parameters.reverse.tail.foldLeft(base) { case (current, methodGroup) =>
          val paramNames = methodGroup.parameters.map(_.name)
          val paramTypes = methodGroup.parameters.map(_.parameterType)
          MethodType(methodGroup.methodTypeKind)(paramNames)(_ => paramTypes, _ => current)
        }
        result
      }
    }

    val cls = Symbol.newClass(Symbol.spliceOwner, implName, parents = parents.map(_.tpe), decls, selfType = None)

    def makeDecode(method: analyzer.Method)(inner: Expr[F[Response]]): Term = {
      type R

      given Type[R]     = method.returnType.typeArgs.head.asType.asInstanceOf[Type[R]]
      val returnDecoder = Expr.summon[ResponseDecoder[R]].getOrElse {
        throw new IllegalArgumentException("Could not find codec for type R " + method.returnType)
      }

      val x = '{ ${ effect }.decodeResponse[R](${ inner })(using $returnDecoder) }
      x.asTerm
    }

    def encodeArg(name: String, dtype: TypeRepr, arg: Tree, request: Expr[Request]): Expr[Request] = {
      type X

      given Type[X]  = dtype.asType.asInstanceOf[Type[X]]
      val paramCodec = Expr.summon[ParamEncoder[X]].getOrElse {
        throw new IllegalArgumentException(s"Could not find ParamCodec for ${dtype.show}")
      }

      '{ ${ paramCodec }.encode(${ Expr(name) }, ${ arg.asExprOf[X] }, ${ request }) }
    }

    def encodeNamedArgs(names: List[String], dtypes: List[TypeRepr], args: List[Tree]): Expr[Request] = {
      val initial: Expr[Request] = '{ Request.empty }
      names.zip(dtypes).zip(args).foldLeft(initial) { case (current, ((name, datatype), arg)) =>
        encodeArg(name, datatype, arg, current)
      }
    }

    def multiArgImplementation(method: analyzer.Method, args: List[List[Tree]]): Term = {
      val request = encodeNamedArgs(method.paramNames, method.paramTypes, args.flatten)
      makeDecode(method) {
        '{ $backend.call(${ Expr(analyze.apiName) }, ${ Expr(method.name) }, ${ request }) }
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
    // println(s"STUB: ${result.show}")
    result
  }
}
