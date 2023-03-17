package kreuzberg.rpc

import scala.annotation.experimental
import scala.quoted.*
import scala.concurrent.Future
import zio.Task

/** A wrapped service. */
trait Dispatcher[F[_], T] {

  /** Returns true if a given service is handled. */
  def handles(serviceName: String): Boolean

  /** Issue a call. */
  def call(serviceName: String, name: String, input: T): F[T]
}

object Dispatcher {

  /** Create a dispatcher for an Interface A */
  inline def makeDispatcher[A](handler: A): Dispatcher[Future, String] = {
    ${ makeDispatcherMacro[Future, String, A]('handler) }
  }

  inline def makeZioDispatcher[A](handler: A): Dispatcher[Task, String] = {
    ${ makeDispatcherMacro[Task, String, A]('handler) }
  }

  /** Create a dispatcher for an Interface A with custom Effect and Transport type. */
  inline def makeCustomDispatcher[F[_], T, A](handler: A): Dispatcher[F, T] = {
    ${ makeDispatcherMacro[F, T, A]('handler) }
  }

  def empty[F[_], T](implicit effect: Effect[F]): Dispatcher[F, T] = new Dispatcher[F, T] {
    override def handles(serviceName: String): Boolean = false

    override def call(serviceName: String, name: String, input: T): F[T] = effect.failure(
      UnknownServiceError("Empty Dispatcher")
    )
  }

  /** Comboine multiple Dispatchers into one. */
  def combine[F[_], T](dispatchers: Dispatcher[F, T]*)(implicit effect: Effect[F]): Dispatcher[F, T] = Dispatchers(
    dispatchers
  )

  @experimental
  def makeDispatcherMacro[F[_], T, A](
      handler: Expr[A]
  )(using Quotes, Type[T], Type[F], Type[A]): Expr[Dispatcher[F, T]] = {
    val analyzer = new TraitAnalyzer(quotes)
    val analyzed = analyzer.analyze[A]

    import analyzer.quotes.reflect.*

    val effect = Expr.summon[Effect[F]].getOrElse {
      throw new IllegalArgumentException(
        "Could not find Effect for F (if you are using Future, there must be an ExecutionContext present)"
      )
    }
    val mc     = Expr.summon[MessageCodec[T]].getOrElse {
      throw new IllegalArgumentException("Could not find MessageCodec for T")
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
            _ => List(TypeRepr.of[String], TypeRepr.of[String], TypeRepr.of[T]),
            _ => TypeRepr.of[F[T]]
          )
        ) ::
        analyzed.methods.map { method =>
          Symbol.newMethod(
            parent = cls,
            name = "call_" + method.name,
            tpe = MethodType(List("input"))(_ => List(TypeRepr.of[T]), _ => TypeRepr.of[F[T]])
          )
        }
    }

    val parents  = List(TypeTree.of[Object], TypeTree.of[Dispatcher[F, T]])
    val implName = analyzed.name + "_dispatcher"
    val cls      = Symbol.newClass(Symbol.spliceOwner, implName, parents = parents.map(_.tpe), decls, selfType = None)

    def callClause(owner: Symbol, args: List[List[Tree]], method: analyzer.Method, elseBlock: Term): Term = {
      val forwardMethod  = "call_" + method.name
      val declaredMethod = cls.declaredMethod(forwardMethod).head
      val ref            = Ref(declaredMethod)

      val callName = args.head.apply(1).asExprOf[String]
      val argument = args.head.apply(2).asExprOf[T]

      val full = Apply(ref, List(argument.asTerm)).asExprOf[F[T]]

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
        ${ effect }.failure[T](UnknownCallError($serviceName, $callName))
      }.asTerm

      val inner = analyzed.methods.foldRight(lastElse) { (method, right) =>
        callClause(owner, args, method, right)
      }

      inner
    }

    def callMethod(parent: Symbol, argss: List[List[Tree]], method: analyzer.Method): Term = {
      val args = argss.head.head.asExprOf[T]

      val inner = ValDef.let(
        parent,
        '{ $mc.split($args, ${ Expr(method.paramNames) }).toTry.get }.asTerm
      ) { params =>
        val paramsExp = params.asExprOf[Seq[T]]

        val decodingTerms = method.paramTypes.zipWithIndex.map { case (dtype, idx) =>
          type X
          given Type[X]     = dtype.asType.asInstanceOf[Type[X]]
          val codec         = Expr.summon[Codec[X, T]].getOrElse {
            throw new IllegalArgumentException("Could not find codec for type X" + dtype)
          }
          val idxExpression = Expr(idx)
          val getExpression = '{ $paramsExp.apply($idxExpression) }
          val decodeTerm    = '{ $codec.decode($getExpression).toTry.get }.asTerm
          decodeTerm
        }

        // Representing the target method
        val ref = Select.unique(handler.asTerm, method.name)

        ValDef.let(parent, decodingTerms) { values =>
          type R
          given Type[R]   = method.returnType.typeArgs.head.asType.asInstanceOf[Type[R]]
          val returnCodec = Expr.summon[Codec[R, T]].getOrElse {
            throw new IllegalArgumentException("Could not find codec for type R" + method.returnType)
          }
          val called      = Apply(ref, values).asExprOf[F[R]]
          '{ $effect.encodeResult($called)($returnCodec) }.asTerm
        }
      }

      '{
        try {
          ${ inner.asExprOf[F[T]] }
        } catch {
          case e: Failure =>
            $effect.failure(e)
        }
      }.asTerm.changeOwner(parent)
    }

    val methodDefinitions = {
      val decl = cls.declaredMethod("handles").head
      DefDef(
        decl,
        argss =>
          Some('{
            ${ argss.head.head.asExprOf[String] } == ${ Expr(analyzed.name) }
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
            if (${ argss.head.head.asExprOf[String] } != ${ Expr(analyzed.name) }) {
              ${ effect }.failure[T](UnknownServiceError(${ argss.head.head.asExprOf[String] }))
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
      Typed(Apply(Select(New(TypeIdent(cls)), cls.primaryConstructor), Nil), TypeTree.of[Dispatcher[F, T]])
    val result = Block(List(clsDef), newCls).asExprOf[Dispatcher[F, T]]
    // println(s"RESULT: ${result.show}")
    result
  }
}
