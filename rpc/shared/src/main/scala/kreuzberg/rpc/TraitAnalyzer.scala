package kreuzberg.rpc
import quoted.*

/** Helper analyzing a trait within a Macro */
private[rpc] class TraitAnalyzer[Q <: Quotes](using val quotes: Q) {
  import quotes.reflect.*
  case class Analyze(
      name: String,
      apiNameOverride: Option[String],
      methods: List[Method]
  ) {

    /** Expression for the API Name. */
    def apiName: String = apiNameOverride.getOrElse(name)
  }

  case class Method(
      name: String,
      symbol: Symbol,
      defDef: DefDef,
      returnType: TypeRepr,
      parameters: List[MethodParameterGroup]
  ) {
    def flatParameters: List[MethodParameter] = {
      parameters.flatMap(_.parameters)
    }

    def paramNames: List[String]   = flatParameters.map(_.name)
    def paramTypes: List[TypeRepr] = flatParameters.map(_.parameterType)
  }

  case class MethodParameterGroup(
      isImplicit: Boolean,
      isGiven: Boolean,
      parameters: List[MethodParameter]
  ) {

    /** Translates to methodTypeKind */
    def methodTypeKind: MethodTypeKind = {
      if (isImplicit) {
        MethodTypeKind.Implicit
      } else if (isGiven) {
        MethodTypeKind.Contextual
      } else {
        MethodTypeKind.Plain
      }
    }
  }

  case class MethodParameter(
      name: String,
      parameterType: TypeRepr
  )

  def analyze[T](using Type[T]): Analyze = {
    val tree = TypeRepr.of[T]

    val toUse  = tree match {
      case a: AppliedType =>
        a.tycon
      case _              =>
        tree
    }
    val symbol = toUse.typeSymbol
    val name   = symbol.name

    val apiName = symbol.annotations.collectFirst {
      case term if (term.tpe <:< TypeRepr.of[ApiName]) =>
        term match {
          case Apply(_, List(Literal(value))) =>
            value.value.asInstanceOf[String]
          case _                              =>
            println(s"Could not decompose ApiName annotation: ${term.show}")
            ???
        }
    }

    val methods = for {
      member <- symbol.methodMembers
      if !member.isClassConstructor
      if !member.flags.is(Flags.Synthetic)
      if member.flags.is(Flags.Deferred)
    } yield {
      val decoded = member.tree.asInstanceOf[DefDef]

      val parameters = decoded.paramss.map { paramClause =>
        val (isGiven, isImplicit) = paramClause match {
          case t: TermParamClause =>
            // println(s"Is Given: ${t.isGiven}")
            // println(s"Is Implicit: ${t.isImplicit}")
            (t.isGiven, t.isImplicit)
          case _                  =>
            // println(s"Not a term param clasue")
            (false, false)
        }
        val parameters            = paramClause.params.map {
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
        MethodParameterGroup(
          isImplicit = isImplicit,
          isGiven = isGiven,
          parameters = parameters
        )
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
      apiNameOverride = apiName,
      methods
    )
  }

}
