package kreuzberg.rpc
import scala.quoted.Quotes
import scala.quoted.Type

/** Helper analyzing a trait within a Macro */
private[rpc] class TraitAnalyzer[Q <: Quotes](val quotes: Q) {
  import quotes.reflect.*
  case class Analyze(
      name: String,
      methods: List[Method]
  )

  case class Method(
      name: String,
      symbol: Symbol,
      defDef: DefDef,
      returnType: TypeRepr,
      parameters: List[List[MethodParameter]]
  ) {
    def flatParameters: List[MethodParameter] = {
      parameters.flatten
    }

    def paramNames: List[String]   = flatParameters.map(_.name)
    def paramTypes: List[TypeRepr] = flatParameters.map(_.parameterType)
  }

  case class MethodParameter(
      name: String,
      parameterType: TypeRepr
  )

  def analyze[T](using Type[T]): Analyze = {
    // import quotes.reflect.*
    val tree = TypeRepr.of[T]
    // println(s"Tree: ${tree}")

    val toUse  = tree match {
      case a: AppliedType =>
        // println("- Is Applied ")
        a.tycon
      case _              =>
        // println("- Not applied")
        tree
    }
    val symbol = toUse.typeSymbol
    val name   = symbol.name

    val methods = for {
      member <- symbol.methodMembers
      if !member.isClassConstructor
      if !member.flags.is(Flags.Synthetic)
      if member.flags.is(Flags.Deferred)
    } yield {
      val decoded = member.tree.asInstanceOf[DefDef]

      val parameters = decoded.paramss.map { paramClause =>
        paramClause.params.map {
          case v: ValDef  =>
            // println(s"Parameter: ${v.name}, ${v.tpt.tpe}")
            MethodParameter(
              v.name,
              v.tpt.tpe
            )
          case p: TypeDef =>
            println("Do not know what to do with ${p}")
            ???
        }
      }
      Method(
        decoded.name,
        member,
        decoded,
        returnType = decoded.returnTpt.tpe,
        parameters = parameters
      )
    }

    Analyze(
      name,
      methods
    )
  }

}
