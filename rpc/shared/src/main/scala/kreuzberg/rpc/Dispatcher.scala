package kreuzberg.rpc

import io.circe.{Decoder, Encoder, Json}

import scala.annotation.experimental
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

    def callClause(owner: Symbol, args: List[List[Tree]], method: analyzer.Method, elseBlock: Term): Term = {
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

    def callMethod(parent: Symbol, argss: List[List[Tree]], method: analyzer.Method): Term = {
      val args = argss.head.head.asExprOf[Request]

      val inner = ValDef.let(
        parent,
        '{ MessageCodec.split(${ args }.payload, ${ Expr(method.paramNames) }).toTry.get }.asTerm
      ) { params =>
        val paramsExp = params.asExprOf[Seq[Json]]

        val decodingTerms = method.paramTypes.zipWithIndex.map { case (dtype, idx) =>
          type X
          given Type[X]     = dtype.asType.asInstanceOf[Type[X]]
          val decoder       = Expr.summon[Decoder[X]].getOrElse {
            throw new IllegalArgumentException("Could not find decoder for type X" + dtype)
          }
          val idxExpression = Expr(idx)
          val getExpression = '{ $paramsExp.apply($idxExpression) }
          val decodeTerm    = '{ $decoder.decodeJson($getExpression).toTry.get }.asTerm
          decodeTerm
        }

        // Representing the target method
        val ref = Select.unique(handler.asTerm, method.name)

        ValDef.let(parent, decodingTerms) { values =>
          type R
          given Type[R]     = method.returnType.typeArgs.head.asType.asInstanceOf[Type[R]]
          val returnEncoder = Expr.summon[Encoder[R]].getOrElse {
            throw new IllegalArgumentException("Could not find codec for type R" + method.returnType)
          }
          val called        = Apply(ref, values).asExprOf[F[R]]
          '{
            $effect.encodeResponse($called)(using $returnEncoder)
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
            given Quotes = decl.asQuotes
            callMethod(decl, argss, method)
          }
      )
    }

    val clsDef = ClassDef(cls, parents, body = methodDefinitions)
    val newCls =
      Typed(Apply(Select(New(TypeIdent(cls)), cls.primaryConstructor), Nil), TypeTree.of[Dispatcher[F]])
    val result = Block(List(clsDef), newCls).asExprOf[Dispatcher[F]]
    // println(s"RESULT: ${result.show}")
    result
  }
}
