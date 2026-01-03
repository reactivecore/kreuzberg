package kreuzberg.rpc

import scala.annotation.{experimental, unused}
import scala.quoted.*
import scala.concurrent.Future

/** A wrapped service. */
trait Dispatcher[F[_]] {

  /** Returns true if a given service is handled. */
  def handles(serviceName: String): Boolean

  /** Issue a call. */
  def call(serviceName: String, name: String, request: Request): F[Response]

  /** Intercept into the request. This way it's possible to e.g. delegate generic security handling. */
  def preRequestFlatMap(f: Request => F[Request])(using effect: EffectSupport[F]): Dispatcher[F] = {
    val outer = this
    new Dispatcher[F] {
      override def handles(serviceName: String): Boolean = outer.handles(serviceName)

      override def call(serviceName: String, name: String, request: Request): F[Response] = {
        effect.flatMap(f(request)) { request =>
          outer.call(serviceName, name, request)
        }
      }
    }
  }

  def asCallingBackend: CallingBackend[F] = new CallingBackend[F] {
    override def call(service: String, name: String, input: Request): F[Response] = {
      Dispatcher.this.call(service, name, input)
    }
  }
}

object Dispatcher {

  /** Create a dispatcher for an Interface A */
  @experimental
  inline def makeDispatcher[A](handler: A): Dispatcher[Future] = {
    ${ makeDispatcherMacro[Future, A]('handler) }
  }

  @experimental
  inline def makeIdDispatcher[A](handler: A): Dispatcher[Id] = {
    ${ makeDispatcherMacro[Id, A]('handler) }
  }

  /** Create a dispatcher for an Interface A with custom Effect and Transport type. */
  @experimental
  inline def makeCustomDispatcher[F[_], A](handler: A): Dispatcher[F] = {
    ${ makeDispatcherMacro[F, A]('handler) }
  }

  def empty[F[_], T](implicit effect: EffectSupport[F]): Dispatcher[F] = new Dispatcher[F] {
    override def handles(serviceName: String): Boolean = false

    override def call(serviceName: String, name: String, input: Request): F[Response] = effect.failure(
      UnknownServiceError("Empty Dispatcher")
    )
  }

  /** Comboine multiple Dispatchers into one. */
  def combine[F[_], T](dispatchers: Dispatcher[F]*)(implicit effect: EffectSupport[F]): Dispatcher[F] =
    Dispatchers(
      dispatchers
    )

  @experimental
  def makeDispatcherMacro[F[_], A](
      handler: Expr[A]
  )(using Quotes, Type[F], Type[A]): Expr[Dispatcher[F]] = {
    val analyzer = new TraitAnalyzer()
    val analyzed = analyzer.analyze[A]

    import analyzer.quotes.reflect.*

    val effect = Expr.summon[EffectSupport[F]].getOrElse {
      throw new IllegalArgumentException(
        "Could not find Effect for F (if you are using Future, there must be an ExecutionContext present)"
      )
    }

    def decls(cls: Symbol): List[Symbol] = {
      Symbol.newMethod(
        parent = cls,
        name = "handles",
        tpe = MethodType(List("serviceName"))(_ => List(TypeRepr.of[String]), _ => TypeRepr.of[Boolean])
      ) ::
        Symbol.newMethod(
          parent = cls,
          name = "call",
          tpe = MethodType(List("serviceName", "name", "input"))(
            _ => List(TypeRepr.of[String], TypeRepr.of[String], TypeRepr.of[Request]),
            _ => TypeRepr.of[F[Response]]
          )
        ) ::
        analyzed.methods.map { method =>
          Symbol.newMethod(
            parent = cls,
            name = "call_" + method.name,
            tpe = MethodType(List("input"))(_ => List(TypeRepr.of[Request]), _ => TypeRepr.of[F[Response]])
          )
        }
    }

    val parents  = List(TypeTree.of[Object], TypeTree.of[Dispatcher[F]])
    val implName = analyzed.name + "_dispatcher"
    val cls      = Symbol.newClass(Symbol.spliceOwner, implName, parents = parents.map(_.tpe), decls, selfType = None)

    def callClause(@unused owner: Symbol, args: List[List[Tree]], method: analyzer.Method, elseBlock: Term): Term = {
      val forwardMethod  = "call_" + method.name
      val declaredMethod = cls.declaredMethod(forwardMethod).head
      val ref            = Ref(declaredMethod)

      val callName = args.head.apply(1).asExprOf[String]
      val argument = args.head.apply(2).asExprOf[Request]

      val full = Apply(ref, List(argument.asTerm)).asExprOf[F[Response]]

      If(
        '{ $callName == ${ Expr(method.name) } }.asTerm,
        full.asTerm,
        elseBlock
      )
    }

    def callClauses(owner: Symbol, args: List[List[Tree]]): Term = {
      val serviceName = args.head.head.asExprOf[String]
      val callName    = args.head.apply(1).asExprOf[String]

      val lastElse = '{
        ${ effect }.failure[Response](UnknownCallError($serviceName, $callName))
      }.asTerm

      val inner = analyzed.methods.foldRight(lastElse) { (method, right) =>
        callClause(owner, args, method, right)
      }

      inner
    }

    def synthetisizeCall(arguments: List[Term], method: analyzer.Method): Term = {
      var splitArguments: List[List[Term]] = Nil // scalafix:ok

      var remainingArgs: List[Term] = arguments // scalafix:ok

      method.parameters.foreach { paramGroup =>
        val groupArgs = remainingArgs.take(paramGroup.parameters.size)
        splitArguments = groupArgs :: splitArguments
        remainingArgs = remainingArgs.drop(paramGroup.parameters.size)
      }
      splitArguments = splitArguments.reverse

      val result = handler.asTerm
        .select(method.symbol)
        .appliedToArgss(splitArguments)

      result
    }

    def callMethod(parent: Symbol, argss: List[List[Tree]], method: analyzer.Method): Term = {
      val args = argss.head.head.asExprOf[Request]

      val inner = {
        val decodingTerms: List[Term] =
          method.paramTypes.zip(method.paramNames).zipWithIndex.map { case ((dtype, paramName), idx) =>
            type X
            given Type[X]    = dtype.asType.asInstanceOf[Type[X]]
            val paramDecoder = Expr.summon[ParamDecoder[X]].getOrElse {
              throw new IllegalArgumentException(s"Could not find ParamCodec for type X: ${dtype.show}, Idx: ${idx}")
            }
            Expr(idx)
            val decodeTerm   = '{ $paramDecoder.decode(${ Expr(paramName) }, $args) }.asTerm: Term
            decodeTerm
          }

        ValDef.let(parent, decodingTerms) { values =>
          type R
          given Type[R]       = method.returnType.typeArgs.head.asType.asInstanceOf[Type[R]]
          val responseBuilder = Expr.summon[ResponseEncoder[R]].getOrElse {
            throw new IllegalArgumentException("Could not find codec for type R" + method.returnType)
          }
          val called          = synthetisizeCall(values, method).asExprOf[F[R]]
          '{
            $effect.encodeResponse($called)(using $responseBuilder)
          }.asTerm
        }
      }

      '{
        try {
          ${ inner.asExprOf[F[Response]] }
        } catch {
          case e: Failure        =>
            $effect.failure[Response](e)
          case c: io.circe.Error =>
            $effect.failure[Response](Failure.fromCirceError(c))
        }
      }.asTerm.changeOwner(parent)
    }

    val methodDefinitions = {
      val decl = cls.declaredMethod("handles").head
      DefDef(
        decl,
        argss =>
          Some('{
            ${ argss.head.head.asExprOf[String] } == ${ Expr(analyzed.apiName) }
          }.asTerm)
      )
    } :: {
      val decl = cls.declaredMethod("call").head
      DefDef(
        decl,
        argss =>
          given Quotes = decl.asQuotes
          val clauses  = callClauses(decl, argss)
          Some('{
            if (${ argss.head.head.asExprOf[String] } != ${ Expr(analyzed.apiName) }) {
              ${ effect }.failure[Response](UnknownServiceError(${ argss.head.head.asExprOf[String] }))
            } else {
              ${ clauses.asExpr }
            }
          }.asTerm)
      )
    } :: analyzed.methods.map { method =>
      val decl = cls.declaredMethod("call_" + method.name).head
      DefDef(
        decl,
        argss =>
          Some {
            callMethod(decl, argss, method)
          }
      )
    }

    val clsDef = ClassDef(cls, parents, body = methodDefinitions)
    val newCls =
      Typed(Apply(Select(New(TypeIdent(cls)), cls.primaryConstructor), Nil), TypeTree.of[Dispatcher[F]])
    val result = Block(List(clsDef), newCls).asExprOf[Dispatcher[F]]
    // println(s"DISPATCHER: ${result.show}")
    result
  }
}
