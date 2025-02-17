package io.exoquery.plugin.trees

import io.decomat.Is
import io.decomat.case
import io.decomat.match
import io.decomat.on
import io.exoquery.BID
import io.exoquery.CapturedBlock
import io.exoquery.Ord
import io.exoquery.xr.SX
import io.exoquery.SelectClauseCapturedBlock
import io.exoquery.xr.SelectClause
import io.exoquery.SqlExpression
import io.exoquery.SqlQuery
import io.exoquery.annotation.CapturedDynamic
import io.exoquery.annotation.Dsl
import io.exoquery.annotation.DslFunctionCall
import io.exoquery.plugin.printing.dumpSimple
import io.exoquery.plugin.transform.TransformerScope
import io.exoquery.parseError
import io.exoquery.parseErrorSym
import io.exoquery.plugin.*
import io.exoquery.plugin.logging.CompileLogger
import io.exoquery.plugin.logging.Messages
import io.exoquery.plugin.transform.LocateableContext
import io.exoquery.plugin.trees.ExtractorsDomain.Call.`(op)x`
import io.exoquery.plugin.trees.ExtractorsDomain.Call.`x to y`
import io.exoquery.plugin.trees.ExtractorsDomain.IsSelectFunction
import io.exoquery.plugin.trees.PT.io_exoquery_util_scaffoldCapFunctionQuery
import io.exoquery.xr.*
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.backend.js.utils.typeArguments
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.name.FqName

data class LocationContext(val internalVars: TransformerScope, override val currentFile: IrFile): LocateableContext {
  fun newParserCtx() = ParserContext(this)
  fun withCapturedFunctionParameters(capturedFunctionParameters: List<IrValueParameter>) =
    LocationContext(internalVars.withCapturedFunctionParameters(capturedFunctionParameters), currentFile)
  fun withSymbols(symbols: List<IrSymbol>) =
    LocationContext(internalVars.withSymbols(symbols), currentFile)
}

data class ParserContext(val location: LocationContext, val binds: DynamicsAccum = DynamicsAccum.newEmpty()): LocateableContext {
  val internalVars get() = location.internalVars
  val capturedFunctionSymbols get() = internalVars.capturedFunctionParameters
  override val currentFile get() = location.currentFile
}

context(ParserContext) private val IrElement.loc get() = this.locationXR()

inline fun <reified R> Is.Companion.of(vararg possibilities: R): Is<R> = Is.PredicateAs(io.decomat.Typed<R>(), { possibilities.contains(it) })

object Parser {
  context(LocationContext, CompileLogger) fun parseFunctionBlockBody(blockBody: IrBlockBody): Pair<XR, DynamicsAccum> =
    with (newParserCtx()) {
      ExpressionParser.parseFunctionBlockBody(blockBody) to binds
    }

  context(LocationContext, CompileLogger) fun parseQuery(expr: IrExpression): Pair<XR.Query, DynamicsAccum> =
    with (newParserCtx()) {
      QueryParser.parse(expr) to binds
    }

  context(LocationContext, CompileLogger) fun parseSelectClauseLambda(expr: IrExpression): Pair<SelectClause, DynamicsAccum> =
    with (newParserCtx()) {
      SelectClauseParser.parseSelectLambda(expr) to binds
    }

  context(LocationContext, CompileLogger) fun parseExpression(expr: IrExpression): Pair<XR.Expression, DynamicsAccum> =
    with (newParserCtx()) {
      ExpressionParser.parse(expr) to binds
    }

  context(LocationContext, CompileLogger) fun parseValueParamter(expr: IrValueParameter): XR.Ident =
    with (newParserCtx()) { expr.makeIdent() }

  context(ParserContext, CompileLogger)
  internal fun parseArg(arg: IrExpression) =
    when {
      arg.type.isClass<SqlQuery<*>>() -> QueryParser.parse(arg)
      else -> ExpressionParser.parse(arg)
    }
}

object QueryParser {

  context(ParserContext, CompileLogger)
  private fun processQueryLambda(head: IrExpression, lambda: IrExpression) =
    lambda.match(
      case(Ir.FunctionExpression.withBlock[Is(), Is()]).thenThis { _, blockBody ->
        val headXR = parse(head)
        val firstParam = firstParam().makeIdent()
        val tailExpr = ExpressionParser.parseFunctionBlockBody(blockBody)
        Triple(headXR, firstParam, tailExpr)
      }
    )

  context(ParserContext, CompileLogger)
  fun IrCall.isDslMethod() =
    this.symbol.owner.let { it.hasAnnotation<Dsl>() } ?: false

  context(ParserContext, CompileLogger) fun parse(expr: IrExpression): XR.Query =
    when {
      // We don't want arbitrary functions returning SqlQuery to be treated as dynamic so we make sure they are annotated with @Dsl
      // this processes everything like that.
      expr is IrCall && expr.ownerHasAnnotation<DslFunctionCall>() -> CallParser.parse(expr).asQuery() ?: parseError("Could not parse the DslCall", expr)
      expr is IrCall && expr.isDslMethod() -> parseDslCall(expr) ?: parseError("Could not parse the DSL call", expr)
      else -> {
        on(expr).match<XR.Query>(
          // If you find a random identifier hanging around, check if:
          // 1. It is coming from a scope outside of the inside of the current function e.g. `capture { val x = Table<Person>; x/*<- we are here*/.map(...) }`
          //    (if it isn't then we know it needs to be a dynamic)
          // 2. It is a captured variable or function argument
//          case(Ir.GetValue[Is()]).thenThis { sym->
//            error("----------- GetValue Owner ---------\n${this.symbol.owner}")
//            XR.Ident(sym.safeName, TypeParser.of(this), this.locationXR())
//          },

          // TODO need to make sure 1st arg is SqlQuery instance and also the owner function has a @CapturedFunction annotate
          //     (also parser should check for any IrFunction that has a @CapturedFunction annotation that doesn't have scaffolding and immediately report an error on that)
          case(Ir.Call.FunctionUntethered2[Is(io_exoquery_util_scaffoldCapFunctionQuery), Is(), Ir.Vararg[Is()]]).thenThis { sqlQueryArg, (args) ->
            val callExpr = this
            val sqlQueryExpr = sqlQueryArg
            val warppedQueryCall =
              sqlQueryExpr.match(
                case(SqlQueryExpr.Uprootable[Is()]).thenThis { uprootable ->
                  val sqlQueryIr = this
                  // Add all binds from the found SqlQuery instance, this will be truned into something like `currLifts + SqlQuery.lifts` late
                  binds.addAllParams(sqlQueryIr)
                  // Then unpack and return the XR
                  uprootable.xr // TODO catch errors here?
                },
                case(ExtractorsDomain.DynamicQueryCall[Is()]).then { _ ->
                  val bid = BID.new()
                  binds.addRuntime(bid, sqlQueryExpr)
                  // need to type the parse
                  XR.TagForSqlQuery(bid, TypeParser.of(sqlQueryExpr), expr.loc)
                }
              ) ?: parseError("Could not parse the SqlQuery from the scaffold call", sqlQueryExpr)
            val parsedArgs = args.map { arg -> arg?.let { Parser.parseArg(it) } ?: XR.Const.Null(callExpr.loc) }
            XR.FunctionApply(warppedQueryCall, parsedArgs, expr.loc)
          },

          case(Ir.GetValue[Is()]).thenIfThis { this.isCapturedVariable() || this.isCapturedFunctionArgument() }.thenThis { sym->
            XR.Ident(sym.safeName, TypeParser.of(this), this.locationXR())
          },
          // If we couldn't parse the expression treat (and it is indeed a SqlQuery<*> treat it as dynamic i.e. non-uprootable
          // Since there's no splice-operator for SqlQuery like there is .use for SqlExpression (i.e. the variable/function-call is used directly)
          // if nothing else matches the expression, we need to look at it in a couple of different ways and then find out if it is a dynamic query
          // TODO When QueryMethodCall and QueryGlobalCall are introduced need to revisit this to see what happens if there is a dynamic call on a query
          //      and how to differentitate it from something that we want to capture. Perhaps we would need some kind of "query-method whitelist"
          case(SqlQueryExpr.Uprootable[Is()]).thenThis { uprootable ->
            val sqlQueryIr = this
            // Add all binds from the found SqlQuery instance, this will be truned into something like `currLifts + SqlQuery.lifts` late
            binds.addAllParams(sqlQueryIr)
            // Then unpack and return the XR
            uprootable.xr // TODO catch errors here?
          },
          // TODO check that the output is a SqlQuery and pass to this parser instead of expression parser?
          case(Ir.Call.FunctionMemN[Ir.Expr.ClassOf<kotlin.Function<*>>(), Is("invoke"), Is()]).thenThis { hostFunction, args ->
            XR.FunctionApply(ExpressionParser.parse(hostFunction), args.map { ExpressionParser.parse(it) }, expr.loc)
          },
          case(ExtractorsDomain.DynamicQueryCall[Is()])
            .thenIf {
              // TODO should disable this Strict-Model by default (when the entire DSL is built out) and make this just a warning by default
              // We don't want arbitrary functions returning SqlQuery to be treated as dynamic (e.g. right now I am working on parsing for .nested
              // and since it doesn't exist yet this case is being hit). Make the user either annotate the type or the function with @CapturedReturn
              // in order to know we shuold actually be doing this. Should use a similar strategy for QueryMethodCall and QueryGlobalCall
              it.type.hasAnnotation<CapturedDynamic>() || it.type.hasAnnotation(FqName("io.exoquery.Captured"))
            }.then { _ ->
              val bid = BID.new()
              binds.addRuntime(bid, expr)
              XR.TagForSqlQuery(bid, TypeParser.of(expr), expr.loc)
          }
        ) ?: run {
          val additionalHelp =
            when {
              expr is IrGetValue && expr.symbol.owner is IrFunction ->
                Messages.VariableComingFromNonCapturedFunction(expr.ownerFunName ?: "<???>")

              expr is IrGetValue ->
                "Lineage: ${expr.showLineage()}"
              else -> ""
            }

          parseError("Could not parse the Query." + (if (additionalHelp.isNotEmpty()) "\n${additionalHelp}" else ""), expr)
        }
      }
    }

  // Assuming everything that gets into here is already annotated with @Dsl
  context(ParserContext, CompileLogger) private fun parseDslCall(expr: IrExpression): XR.Query? =
    // Note, every single instance being parsed here shuold be of SqlQuery<*>, should check for that as an entry sanity-check
    on(expr).match<XR.Query>(
      case(Ir.Call.FunctionMem1[Ir.Expr.ClassOf<SqlQuery<*>>(), Is.of("map", "concatMap", "filter"), Is()]).thenThis { head, lambda ->
        val (head, id, body) = processQueryLambda(head, lambda) ?: parseError("Could not parse XR.Map/ConcatMap/Filter", expr)
        when (symName) {
          "map" -> XR.Map(head, id, body, expr.loc)
          "concatMap" -> XR.ConcatMap(head, id, body, expr.loc)
          "filter" -> XR.Filter(head, id, body, expr.loc)
          else -> parseError("Unknown SqlQuery method call: ${symName} in: ${expr.dumpKotlinLike()}", expr)
        }
      },
      case(Ir.Call.FunctionMem1[Ir.Expr.ClassOf<SqlQuery<*>>(), Is("flatMap"), Is()]).thenThis { head, lambda ->
          lambda.match(
            case(Ir.FunctionExpression.withReturnOnlyBlock[Is()]).thenThis { tail ->
              XR.FlatMap(parse(head), firstParam().makeIdent(), parse(tail), expr.loc)
            }
            // TODO for this error message need to have a advanced "mode" that will print out the RAW IR
          ) ?: parseError("SqlQuery.flatMap(...) lambdas can only be single-statement expressions, they cannot be block-lambdas like:\n${lambda.dumpKotlinLike()}\n-----------------------------------\n${lambda.dumpSimple()}", lambda)
      },
      case(Ir.Call.FunctionMem0[Ir.Expr.ClassOf<SqlQuery<*>>(), Is.of("distinct", "nested")]).thenThis { head, _ ->
        when (symName) {
          "distinct" -> XR.Distinct(parse(head), expr.loc)
          "nested" -> XR.Nested(parse(head), expr.loc)
          else -> parseErrorSym(this)
        }
      },
      case(Ir.Call.FunctionMem1[Ir.Expr.ClassOf<SqlQuery<*>>(), Is.of("sortedBy", "sortedByDescending"), Ir.FunctionExpression.withBlock[Is(), Is()]]).thenThis { head, (params, body) ->
        when (symName) {
          "sortedBy" -> XR.SortBy(parse(head), params.first().makeIdent(), ExpressionParser.parseFunctionBlockBody(body), XR.Ordering.Asc, expr.loc)
          "sortedByDescending" -> XR.SortBy(parse(head), params.first().makeIdent(), ExpressionParser.parseFunctionBlockBody(body), XR.Ordering.Desc, expr.loc)
          else -> parseErrorSym(this)
        }
      },
      case(Ir.Call.FunctionMem1[Ir.Expr.ClassOf<SqlQuery<*>>(), Is.of("take", "drop"), Is()]).thenThis { head, num ->
        when (symName) {
          "take" -> XR.Take(parse(head), ExpressionParser.parse(num), expr.loc)
          "drop" -> XR.Drop(parse(head), ExpressionParser.parse(num), expr.loc)
          else -> parseErrorSym(this)
        }
      },
      case(Ir.Call.FunctionMem1[Ir.Expr.ClassOf<SqlQuery<*>>(), Is.of("union", "unionAll"), Is()]).thenThis { head, tail ->
        val tailXR = parse(tail)
        when (symName) {
          "union" -> XR.Union(parse(head), tailXR, expr.loc)
          "unionAll" -> XR.UnionAll(parse(head), tailXR, expr.loc)
          else -> parseErrorSym(this)
        }
      },
      case(Ir.Call.FunctionMem0[Ir.Expr.ClassOf<CapturedBlock>(), Is("Table")]).thenThis { _, _ ->
        val tpe = TypeParser.ofTypeAt(this.typeArguments[0] ?: parseError("Type arguemnt of Table() call was not found>"), this.location())
        val tpeProd = tpe as? XRType.Product ?: parseError("Table<???>() call argument type must be a data-class, but was: ${tpe}", expr)
        XR.Entity(tpeProd.name, tpeProd, expr.locationXR())
      },
      // This is the select defined in the capture-block (that returns SqlQuery<T> as opposed to the top-level one which returns @Captured SqlQuery<T>.
      case(Ir.Call.FunctionMem1[Ir.Expr.ClassOf<CapturedBlock>(), Is("select"), Ir.FunctionExpression[Is()]]).thenThis { _, (selectLambda) ->
        XR.CustomQueryRef(SelectClauseParser.parseSelectLambda(selectLambda))
      },
    )
}

context(ParserContext, CompileLogger)
fun IrValueParameter.makeIdent() =
  XR.Ident(this.name.asString(), TypeParser.of(this), this.locationXR())

fun IrFunctionExpression.firstParam() =
  this.function.simpleValueParams[0]

// Parser for GlobalCall and MethodCall
object CallParser {
  context(ParserContext, CompileLogger)
  fun parse(expr: IrCall): XR.U.QueryOrExpression {
    fun FqName.toFqNameXR() = run {
      this.shortNameOrSpecial()
      XR.FqName(this.pathSegments().joinToString("."), this.shortNameOrSpecial().asString())
    }
    fun IrSimpleFunctionSymbol.toFqNameXR() =
      this.owner.kotlinFqName.toFqNameXR()
    val reciever = expr.extensionReceiver ?: expr.dispatchReceiver

    return if (reciever == null || reciever.type.isClass<CapturedBlock>()) {
      XR.GlobalCall(
        name = expr.symbol.toFqNameXR(),
        args = expr.simpleValueArgs.map { arg -> arg?.let { Parser.parseArg(it) } ?: XR.Const.Null() },
        callType = expr.extractCallType(),
        type = TypeParser.of(expr),
        loc = expr.loc
      )
    } else {
      XR.MethodCall(
        head = Parser.parseArg(reciever!!),
        name = expr.symbol.safeName,
        args = expr.simpleValueArgs.map { arg -> arg?.let { Parser.parseArg(it) } ?: XR.Const.Null() },
        originalHostType = expr.type.classFqName?.toFqNameXR() ?: XR.FqName("", ""),
        type = TypeParser.of(expr),
        loc = expr.loc,
        callType = expr.extractCallType()
      )
    }
  }

  context(ParserContext, CompileLogger)
  private fun IrCall.extractCallType(): XR.CallType {
    val arg =
      (this.symbol.owner.getAnnotationArgs<DslFunctionCall>().firstOrNull() ?: parseError("Could not find DslFunctionCall annotation", this))
        as? IrClassReference ?: parseError("DslFunctionCall annotation must have a single argument that is a class-reference (e.g. PureFunction::class)", this)
    val argXR =
      arg.classType.classFqName?.shortName()?.asString()?.let { XR.CallType.fromClassString(it) }
        ?: parseError("Could not parse CallType from: ${arg.dumpKotlinLike()}", arg)
    return argXR
  }



}

object SelectClauseParser {
  context(ParserContext, CompileLogger) fun processSelectLambda(statementsFromRet: List<IrStatement>, loc: CompilerMessageSourceLocation): SelectClause {
    if (statementsFromRet.isEmpty()) parseError("A select-clause usually should have two statements, a from(query) and an output. This one has neither", loc) // TODO provide example in the error
    if (statementsFromRet.last() !is IrReturn) parseError("A select-clause must return a plain (i.e. not SqlQuery) value.", loc)
    val ret = statementsFromRet.last()
    val retXR = ExpressionParser.parse((ret as IrReturn).value)
    if (ret !is IrReturn) parseError("The last statement in a select-clause must be a return statement", ret) // TODO provide example in the error
    val statementsFrom = statementsFromRet.dropLast(1)
    if (statementsFrom.isEmpty()) SelectClause.justSelect(retXR, loc.toLocationXR())

    val statementsToParsed = statementsFrom.map { parseSubClause(it) to it }
    return ValidateAndOrganize(statementsToParsed, retXR)
  }

  context(ParserContext, CompileLogger) fun parseSelectLambda(lambda: IrStatement): SelectClause =
    lambda.match(
      // this typically happens when the top-level select is called
      case(Ir.FunctionExpression.withBlockStatements[Is(), Is()]).thenThis { _, statementsFromRet ->
        processSelectLambda(statementsFromRet, lambda.location())
      },
      // this typiclally happens when select inside of a CapturedBlock
      case(Ir.SimpleFunction[Is(), Is()]).thenThis { _, body ->
        processSelectLambda(body.statements, lambda.location())
      }
    ) ?: parseError("Could not parse Select Clause from: ${lambda.dumpSimple()}", lambda)

  // need to test case of `select { from(x.map(select { ... })) }` to see how nested recursion works
  // also test case of `select { from(select { ... } }` to see how nested recursion works
  context(ParserContext, CompileLogger) fun parseSubClause(expr: IrStatement): SX =
    on(expr).match<SX>(
      case(Ir.Variable[Is(), Ir.Call.FunctionMem1[Ir.Expr.ClassOf<SelectClauseCapturedBlock>(), Is("from"), Is()]]).thenThis { varName, (_, table) ->
        val id = XR.Ident(varName, TypeParser.of(this), this.loc)
        SX.From(id, QueryParser.parse(table))
      },
      case(Ir.Variable[Is(), Ir.Call.FunctionMem2[IsSelectFunction(), Is.invoke { it == "join" || it == "joinLeft" }, Is()]]).then { varName, (_, args) ->
        val joinFunc = compRight
        val (onTable, joinCondLambda) = args
        val varNameIdent = XR.Ident(varName, TypeParser.of(this.comp), this.comp.loc)
        val joinType =
          when(joinFunc.symbol.safeName) {
            "join" -> XR.JoinType.Inner
            "joinLeft" -> XR.JoinType.Left
            else -> parseError("Unknown Join Type: ${joinFunc.symbol.safeName}", expr)
          }
        joinCondLambda.match(
          case(Ir.FunctionExpression.withBlock[Is(), Is()]).then { lambdaParams, stmtsAndReturn ->
            val lambdaParam = lambdaParams.first()
            val lambdaVarName = lambdaParam.name.asString() /* join lambda should have only one element e.g. join(Table<Addresses>()){addressesLambdaVar ->addressesLambdaVar == 123} */
            val lambdaVarIdent = XR.Ident(lambdaVarName, TypeParser.of(lambdaParam), lambdaParam.loc)
            val joinCond = ExpressionParser.parseFunctionBlockBody(stmtsAndReturn)
            SX.Join(joinType, varNameIdent, QueryParser.parse(onTable), lambdaVarIdent, joinCond, joinFunc.loc)

          }
        ) ?: parseError("Could not parse Join Lambda from: ${joinCondLambda.dumpSimple()}", joinCondLambda)
      },
      // where(() -> Boolean)
      case(Ir.Call.FunctionMem1[IsSelectFunction(), Is("where"), Ir.FunctionExpression.withBlock[Is(), Is()]]).thenThis { _, (_, body) ->
        val whereCond = ExpressionParser.parseFunctionBlockBody(body)
        SX.Where(whereCond, this.loc)
      },
      // where(Boolean)
      case(Ir.Call.FunctionMem1[IsSelectFunction(), Is("where"), Ir.Expr.ClassOf<Boolean>()]).thenThis { _, argValue ->
        SX.Where(ExpressionParser.parse(argValue), this.loc)
      },
      // groupBy(...Any)
      case(Ir.Call.FunctionMemVararg[IsSelectFunction(), Is("groupBy"), Is(), Is()]).thenThis { _, argValues ->
        val groupings = argValues.map { ExpressionParser.parse(it) }
        if (groupings.size == 1) {
          SX.GroupBy(groupings.first(), this.loc)
        }
        else {
          SX.GroupBy(XR.Product.TupleSmartN(groupings, this.loc), this.loc)
        }
      },
      // sortBy(...Pair<*, Ord>)
      case(Ir.Call.FunctionMemVararg[IsSelectFunction(), Is("sortBy"), Ir.Type.ClassOfType<Pair<*, *>>(), Is()]).thenThis { _, argValues ->
        val clausesRaw = argValues.map { OrderParser.parseOrdTuple(it) }
        if (clausesRaw.size == 1) {
          val (expr, ord) = clausesRaw.first()
          SX.SortBy(expr, ord, this.loc)
        }
        else {
          val (exprs, clauses) = clausesRaw.unzip()
          SX.SortBy(XR.Product.TupleSmartN(exprs, this.loc), XR.Ordering.TupleOrdering(clauses), this.loc)
        }
      },
    ) ?: parseError("Could not parse Select Clause from: ${expr.dumpSimple()}", expr)
}

object OrderParser {
  // Can either be `x to Ord` or Pair(x, Ord)
  context(ParserContext, CompileLogger) fun parseOrdTuple(expr: IrExpression): Pair<XR.Expression, XR.Ordering> =
    expr.match(
      case(`x to y`[Is(), Is()]).thenThis { property, ord ->
        ExpressionParser.parse(property) to parseOrd(ord)
      }
    ) ?: parseError("Could not parse a proper ordering from the expression: ${expr.dumpSimple()}. Orderings must always come in the form `property to Ord` for example `person.name to Desc`.", expr)

  context(ParserContext, CompileLogger) fun parseOrd(expr: IrExpression): XR.Ordering =
    expr.match(
      case(Ir.Expr.ClassOf<Ord.Asc>()).then { XR.Ordering.Asc },
      case(Ir.Expr.ClassOf<Ord.Desc>()).then { XR.Ordering.Desc },
      case(Ir.Expr.ClassOf<Ord.AscNullsFirst>()).then { XR.Ordering.AscNullsFirst },
      case(Ir.Expr.ClassOf<Ord.DescNullsFirst>()).then { XR.Ordering.DescNullsFirst },
      case(Ir.Expr.ClassOf<Ord.AscNullsLast>()).then { XR.Ordering.AscNullsLast },
      case(Ir.Expr.ClassOf<Ord.DescNullsLast>()).then { XR.Ordering.DescNullsLast },
    ) ?: parseError("Could not parse an ordering from the expression: ${expr.dumpSimple()}. Orderings must be specified as one of the following compile-time constant values: Asc, Desc, AscNullsFirst, DescNullsFirst, AscNullsLast, DescNullsLast", expr)
}

/**
 * Parses the tree and collets dynamic binds as it goes. The parser should be exposed
 * as stateless to client functions so everything should go through the `Parser` object instead of this.
 */
object ExpressionParser {
  // TODO need to parse interpolations

  context(ParserContext, CompileLogger) fun parseBlockStatement(expr: IrStatement): XR.Variable =
    on(expr).match(
      case(Ir.Variable[Is(), Is()]).thenThis { name, rhs ->
        val irType = TypeParser.of(this)
        XR.Variable(XR.Ident(name, irType, rhs.locationXR()), parse(rhs), expr.loc)
      }
    ) ?: parseError("Could not parse Ir Variable statement from:\n${expr.dumpSimple()}")

  context(ParserContext, CompileLogger) fun parseBranch(expr: IrBranch): XR.Branch =
    on(expr).match(
      case(Ir.Branch[Is(), Is()]).then { cond, then ->
        XR.Branch(parse(cond), parse(then), expr.loc)
      }
    ) ?: parseError("Could not parse Branch from: ${expr.dumpSimple()}")

  context(ParserContext, CompileLogger) fun parseFunctionBlockBody(blockBody: IrBlockBody): XR.Expression =
    blockBody.match(
      case(Ir.BlockBody.ReturnOnly[Is()]).then { irReturnValue ->
        parse(irReturnValue)
      },
      case(Ir.BlockBody.StatementsWithReturn[Is(), Is()]).then { stmts, ret ->
          val vars = stmts.map { parseBlockStatement(it) }
          val retExpr = parse(ret)
          XR.Block(vars, retExpr, blockBody.locationXR())
        }
    ) ?: parseError("Could not parse IrBlockBody:\n${blockBody.dumpKotlinLike()}")


  context(ParserContext, CompileLogger) fun parse(expr: IrExpression): XR.Expression =
    on(expr).match<XR.Expression>(

      case(Ir.Call[Is()]).thenIf { it.ownerHasAnnotation<DslFunctionCall>() }.then { call ->
        CallParser.parse(call).asExpr()
      },

      //case(Ir.Call.FunctionMem0[Ir.Expr.ClassOf<SqlQuery<*>>(), Is("isNotEmpty")]).then { sqlQueryIr, _ ->
      //  XR.QueryToExpr(QueryParser.parse(sqlQueryIr), sqlQueryIr.loc)
      //},

      case(Ir.Expr.ClassOf<SqlQuery<*>>()).then { expr ->
        XR.QueryToExpr(QueryParser.parse(expr), expr.loc)
      },

      case(ExtractorsDomain.CaseClassConstructorCall[Is()]).then { data ->
        XR.Product(data.className, data.fields.map { (name, valueOpt) -> name to (valueOpt?.let { parse(it) } ?: XR.Const.Null(expr.loc)) }, expr.loc)
      },

      // parse lambda in a capture block
      case(Ir.FunctionExpression.withBlock[Is(), Is()]).thenThis { params, blockBody ->
        XR.FunctionN(params.map { it.makeIdent() }, parseFunctionBlockBody(blockBody), expr.loc)
      },

      case(Ir.Call.FunctionMemN[Ir.Expr.ClassOf<kotlin.Function<*>>(), Is("invoke"), Is()]).thenThis { hostFunction, args ->
        XR.FunctionApply(parse(hostFunction), args.map { parse(it) }, expr.loc)
      },

      case(Ir.Call.FunctionMem1.WithCaller[Is(), Is("param"), Is()]).thenThis { _, paramValue ->
        val bid = BID.new()
        binds.addParam(bid, paramValue)
        XR.TagForParam(bid, TypeParser.of(paramValue), paramValue.loc)
      },

      case(Ir.Call.FunctionMem0[Is(), Is("value")]).thenIf { useExpr, _ -> useExpr.type.isClass<SqlQuery<*>>() }.then { sqlQueryIr, _ ->
        XR.QueryToExpr(QueryParser.parse(sqlQueryIr), sqlQueryIr.loc)
      },
      // Now the same for SqlExpression
      // TODO check that the extension reciever is Ir.Expr.ClassOf<SqlExpression<*>> (and the dispatch method is CapturedBlock)
      case(Ir.Call.FunctionMem0[Is(), Is("use")]).thenIf { useExpr, _ -> useExpr.type.isClass<SqlExpression<*>>() }.then { sqlExprIr, _ ->
        sqlExprIr.match(
          case(SqlExpressionExpr.Uprootable[Is()]).then { uprootable ->
            // Add all binds from the found SqlExpression instance, this will be truned into something like `currLifts + SqlExpression.lifts` late
            binds.addAllParams(sqlExprIr)
            // Then unpack and return the XR
            uprootable.xr
          }
          // TODO add dynamic case
        ) ?: run {
          val bid = BID.new()
          binds.addRuntime(bid, sqlExprIr)
          XR.TagForSqlExpression(bid, TypeParser.of(sqlExprIr), sqlExprIr.loc)
        }
      },


      /*
      case(Ir.Call.FunctionMem0[Is(), Is("use")]).thenIf { calledFrom, _ -> calledFrom is IrCall && calledFrom.type.isClass<io.exoquery.SqlExpression<*>>() }.thenThis { calledFrom, _ ->
        //sym.owner.match(
        //  case(Ir.Variable[Is(), SqlExpressionExpr.Uprootable[Is()]]).thenThis { varName, (uprootable) ->
        //    error("----------------- Got to Owner of ------------\n${uprootable.xr.show()}")
        //    uprootable.xr
        //  }
        //)

        error("""
          |------------- Get of SqlExpression -------------
          |${calledFrom.dumpKotlinLike()}
          |------- with IR: --------
          |${calledFrom.dumpSimple()}
          |-----------------------------------------------
          |with Owner :
          |${(calledFrom as IrCall).symbol.owner.dumpKotlinLike()}
          |------- with Owner IR: --------
          |${(calledFrom as IrCall).symbol.owner.dumpSimple()}
          """.trimMargin())
        XR.Const.String("foo")
      },
       */

      //case(Ir.Call.FunctionMem0[Is(), Is("use")]).then { v, _ ->
      //  error("------------ Calling Use Function ---------\n${v.dumpKotlinLike()}")
      //  XR.Const.String("foo")
      //},

      // Binary Operators
      case(ExtractorsDomain.Call.`x op y`[Is()]).thenThis { opCall ->
        val (x, op, y) = opCall
        XR.BinaryOp(parse(x), op, parse(y), expr.loc)
      },
      // Unary Operators
      case(`(op)x`[Is()]).thenThis { opCall ->
        val (x, op) = opCall
        XR.UnaryOp(op, parse(x), expr.loc)
      },

      case(`x to y`[Is(), Is()]).thenThis { x, y ->
        XR.Product.Tuple(parse(x), parse(y), expr.loc)
      },

      // Other situations where you might have an identifier which is not an SqlVar e.g. with variable bindings in a Block (inside an expression)
      case(Ir.GetValue[Is()]).thenIfThis { this.isCapturedVariable() || this.isCapturedFunctionArgument() }.thenThis { sym ->
        XR.Ident(sym.safeName, TypeParser.of(this), this.locationXR()) // this.symbol.owner.type
      },
      case(Ir.Const[Is()]).thenThis {
        parseConst(this)
      },
      case(Ir.Call.Property[Is(), Is()]).then { expr, name ->
        XR.Property(parse(expr), name, XR.Visibility.Visible, expr.loc)
      },

      // case(Ir.Call.Function[Is()]).thenIf { (list) -> list.size == 2 }.thenThis { list ->
      //   val a = list.get(0)
      //   val b = list.get(1)
      //   // TODO need to check that's its actually a binary operator!!!
      //   XR.BinaryOp(parse(a), parseSymbol(this.symbol), parse(b))
      // }
      // ,
      case(Ir.Block[Is(), Is()]).then { stmts, ret ->
        XR.Block(stmts.map { parseBlockStatement(it) }, parse(ret), expr.loc)
      },
      case(Ir.When[Is()]).thenThis { cases ->
        val elseBranch = cases.find { it is IrElseBranch }?.let { parseBranch(it) }
        val casesAst = cases.filterNot { it is IrElseBranch }.map { parseBranch(it) }
        val allReturnsAreBoolean = cases.all { it.result.type.isClass<Boolean>() }

        // Kotlin converts (A && B) to `if(A) B else false`. This undoes that
        if (
            allReturnsAreBoolean &&
            elseBranch != null && casesAst.size == 1
              && casesAst.first().then.type is XRType.Boolean
              // Implicitly the else-clause in this case cannot have additional conditions
              && elseBranch.cond == XR.Const.Boolean(true) && elseBranch.then == XR.Const.Boolean(false)
          ) {
          val firstClause = casesAst.first()
          firstClause.cond `+and+` firstClause.then
        }
        // Kotlin converts (A || B) to `if(A) true else B`. This undoes that
        else if (
          allReturnsAreBoolean &&
          elseBranch != null && casesAst.size == 1
          && casesAst.first().then == XR.Const.Boolean(true)
          // Implicitly the else-clause in this case cannot have additional conditions
          && elseBranch.cond == XR.Const.Boolean(true)
        ) {
          val firstClause = casesAst.first()
          firstClause.cond `+or+` elseBranch.then
        }
        else {
          val elseBranchOrLast = elseBranch ?: casesAst.lastOrNull() ?: parseError("Empty when expression not allowed:\n${this.dumpKotlinLike()}")
          XR.When(casesAst, elseBranchOrLast.then, expr.loc)
        }
      },
      // I.e. a physical lambda applied in an expression e.g:
      // captureValue { { x: Int -> x + 1 } }
      //case(Ir.FunctionExpression.withBlock[Is(), Is()]).thenThis { params, blockBody ->
      //  XR.FunctionN(params.map { it.makeIdent() }, parseFunctionBlockBody(blockBody), expr.loc)
      //},



      // Need to allow possibility of nulls here because even simple things like String.split can have nulls (i.e. for the 2nd/3rd args)
//      case(Ir.Call.FunctionMemAllowNulls[Is(), Is()]).thenIfThis { caller, args ->
//        methodWhitelist.containsMethod(symbol.safeName)
//      }.thenThis { caller, args ->
//        val methodCallName = XR.MethodCallName(symbol.fqNameXR(), caller.reciver.type.fqNameXR())
//        if (!methodWhitelist.contains(methodCallName))
//          throwParseErrorMsg(expr, "The expression was not in the whitelist.", "The expression was: \n${methodCallName}")
//
//        fun parseArg(expr: IrExpression): List<XR.Expression> =
//          when (expr) {
//            is IrVararg -> {
//              expr.elements.toList().map { elem ->
//                when (elem) {
//                  is IrExpression -> parse(elem)
//                  else -> throwParseErrorMsg(elem, "Invalid Variadic Element")
//                }
//              }
//            }
//            else -> listOf(parse(expr))
//          }
//
//        XR.MethodCall(
//          parse(caller.reciver), methodCallName, args.flatMap { arg -> arg?.let { parseArg(it) } ?: listOf(XR.Const.Null(locationXR())) },
//          TypeParser.of(symbol.owner), locationXR()
//        )
//      },
    ) ?: parseError("Could not parse the expression.", expr)

//  fun IrSimpleFunctionSymbol.fqNameXR() =
//    XR.FqName(this.owner.parent.kotlinFqName.toString(), this.safeName)
//
//  fun IrType.fqNameXR() =
//    this.classFqName?.let { name ->
//      XR.FqName(name.parent().toString(), name.shortName().asString())
//    } ?: parseError("Could not get the classFqName for the type: ${this.dumpKotlinLike()}")


  context (CompileLogger, ParserContext) fun throwParseErrorMsg(expr: IrElement, heading: String = "Could not parse expression from", additionalMsg: String = ""): Nothing {
    parseError(
      """|${expr.location().show()}}
         |======= ${heading}: =======
         |${expr.dumpKotlinLike()}
         |--------- With the Tree ---------
         |${expr.dumpSimple()}
      """.trimMargin() + if (additionalMsg != "") "\n" + "--------- Additional Data ---------\n" + additionalMsg else ""
    )
  }

  context (ParserContext, CompileLogger) fun parseConst(irConst: IrConst): XR.Expression =
    if (irConst.value == null) XR.Const.Null(irConst.loc)
    else when (irConst.kind) {
      IrConstKind.Null -> XR.Const.Null(irConst.loc)
      IrConstKind.Boolean -> XR.Const.Boolean(irConst.value as kotlin.Boolean, irConst.loc)
      IrConstKind.Char -> XR.Const.Char(irConst.value as kotlin.Char, irConst.loc)
      IrConstKind.Byte -> XR.Const.Byte(irConst.value as kotlin.Int, irConst.loc)
      IrConstKind.Short -> XR.Const.Short(irConst.value as kotlin.Short, irConst.loc)
      IrConstKind.Int -> XR.Const.Int(irConst.value as kotlin.Int, irConst.loc)
      IrConstKind.Long -> XR.Const.Long(irConst.value as kotlin.Long, irConst.loc)
      IrConstKind.String -> XR.Const.String(irConst.value as kotlin.String, irConst.loc)
      IrConstKind.Float -> XR.Const.Float(irConst.value as kotlin.Float, irConst.loc)
      IrConstKind.Double -> XR.Const.Double(irConst.value as kotlin.Double, irConst.loc)
      else -> parseError("Unknown IrConstKind: ${irConst.kind}")
    }


}
