package io.exoquery.plugin.trees

import io.decomat.Is
import io.decomat.case
import io.decomat.match
import io.decomat.on
import io.exoquery.BID
import io.exoquery.Params
import io.exoquery.SqlExpression
import io.exoquery.SqlQuery
import io.exoquery.annotation.DslFunctionCall
import io.exoquery.annotation.DslNestingIgnore
import io.exoquery.annotation.ParamCtx
import io.exoquery.annotation.ParamCustom
import io.exoquery.annotation.ParamCustomValue
import io.exoquery.annotation.ParamPrimitive
import io.exoquery.annotation.ParamStatic
import io.exoquery.parseError
import io.exoquery.plugin.classId
import io.exoquery.plugin.classIdOf
import io.exoquery.plugin.funName
import io.exoquery.plugin.getAnnotationArgs
import io.exoquery.plugin.isClass
import io.exoquery.plugin.isClassStrict
import io.exoquery.plugin.loc
import io.exoquery.plugin.location
import io.exoquery.plugin.locationXR
import io.exoquery.plugin.logging.CompileLogger
import io.exoquery.plugin.ownerHasAnnotation
import io.exoquery.plugin.printing.dumpSimple
import io.exoquery.plugin.safeName
import io.exoquery.plugin.show
import io.exoquery.plugin.toXR
import io.exoquery.serial.ParamSerializer
import io.exoquery.terpal.UnzipPartsParams
import io.exoquery.xr.`+and+`
import io.exoquery.xr.`+or+`
import io.exoquery.xr.CID
import io.exoquery.xr.XR
import io.exoquery.xr.XRType
import io.exoquery.xr.isConverterFunction
import io.exoquery.xr.isNumeric
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.backend.js.utils.typeArguments
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrBranch
import org.jetbrains.kotlin.ir.expressions.IrClassReference
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstKind
import org.jetbrains.kotlin.ir.expressions.IrElseBranch
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.isBoolean
import org.jetbrains.kotlin.ir.types.isChar
import org.jetbrains.kotlin.ir.types.isDouble
import org.jetbrains.kotlin.ir.types.isFloat
import org.jetbrains.kotlin.ir.types.isInt
import org.jetbrains.kotlin.ir.types.isLong
import org.jetbrains.kotlin.ir.types.isShort
import org.jetbrains.kotlin.ir.types.isString
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.name.ClassId

/**
 * Parses the tree and collets dynamic binds as it goes. The parser should be exposed
 * as stateless to client functions so everything should go through the `Parser` object instead of this.
 */
object ParseExpression {
  internal sealed interface Seg {
    data class Const(val value: String) : Seg {
      fun mergeWith(other: Const): Const = Const(value + other.value)
    }
    data class Expr(val expr: IrExpression) : Seg
    companion object {
      fun parse(expr: IrExpression): Seg =
        when {
          expr.isClassStrict<String>() && expr is IrConst && expr.kind == IrConstKind.String -> Const(expr.value as String)
          else -> Expr(expr)
        }
    }
    context(ParserContext, CompileLogger)
    fun constOrFail(): Const =
      when (this) {
        is Const -> this
        is Expr -> parseError("Expected a constant segment, but found an expression segment: Seg.Expr(${expr.dumpKotlinLike()})", this.expr)
      }
    context(ParserContext, CompileLogger)
    fun exprOrFail(): Expr =
      when (this) {
        is Const -> parseError("Expected an expression segment, but found a constant segment: Seg.Const(${value})")
        is Expr -> this
      }
  }

  context(ParserContext, CompileLogger) fun parseBlockStatement(expr: IrStatement): XR.Variable =
    on(expr).match(
      case(Ir.Variable[Is.Companion(), Is.Companion()]).thenThis { name, rhs ->
        val irType = TypeParser.of(this)
        XR.Variable(XR.Ident(name, irType, rhs.locationXR()), parse(rhs), expr.loc)
      }
    ) ?: parseError("Could not parse Ir Variable statement from:\n${expr.dumpSimple()}")

  context(ParserContext, CompileLogger) fun parseBranch(expr: IrBranch): XR.Branch =
    on(expr).match(
      case(Ir.Branch[Is.Companion(), Is.Companion()]).then { cond, then ->
        XR.Branch(parse(cond), parse(then), expr.loc)
      }
    ) ?: parseError("Could not parse Branch from: ${expr.dumpSimple()}")

  context(ParserContext, CompileLogger) fun parseFunctionBlockBody(blockBody: IrBlockBody): XR.Expression =
    blockBody.match(
      case(Ir.BlockBody.ReturnOnly[Is.Companion()]).then { irReturnValue ->
        parse(irReturnValue)
      },
      case(Ir.BlockBody.StatementsWithReturn[Is.Companion(), Is.Companion()]).then { stmts, ret ->
          val vars = stmts.map { parseBlockStatement(it) }
          val retExpr = parse(ret)
          XR.Block(vars, retExpr, blockBody.locationXR())
        }
    ) ?: parseError("Could not parse IrBlockBody:\n${blockBody.dumpKotlinLike()}")

  context(ParserContext, CompileLogger) fun parse(expr: IrExpression): XR.Expression =
    on(expr).match<XR.Expression>(

      case(Ir.Call[Is.Companion()]).thenIf { it.ownerHasAnnotation<DslFunctionCall>() || it.ownerHasAnnotation<DslNestingIgnore>() }.then { call ->
        CallParser.parse(call).asExpr()
      },

      //case(Ir.Call.FunctionMem0[Ir.Expr.ClassOf<SqlQuery<*>>(), Is("isNotEmpty")]).then { sqlQueryIr, _ ->
      //  XR.QueryToExpr(QueryParser.parse(sqlQueryIr), sqlQueryIr.loc)
      //},

      // Converter functions for string e.g. toInt, toLong, etc.
      case(Ir.Call.FunctionMem0[Ir.Expr.ClassOf<String>(), Is.Companion { it.isConverterFunction() }]).then { head, method ->
        XR.MethodCall(parse(head), method, emptyList(), XR.CallType.PureFunction, CID.kotlin_String, XRType.Value, expr.loc)
      },

      // Numeric conversion functions toInt, toString, etc... on numeric types Int, Long, etc...
      case(Ir.Call.FunctionMem0[Is.Companion(), Is.Companion { it.isConverterFunction() }])
        .thenIf { head, _ -> head.type.classId()?.toXR()?.isNumeric() ?: false }
        .then { head, method ->
          XR.MethodCall(parse(head), method, emptyList(), XR.CallType.PureFunction, CID.kotlin_String, XRType.Value, expr.loc)
        },

      case(Ir.Expr.ClassOf<SqlQuery<*>>()).then { expr ->
        XR.QueryToExpr(ParseQuery.parse(expr), expr.loc)
      },

      case(ExtractorsDomain.CaseClassConstructorCall[Is.Companion()]).then { data ->
        XR.Product(data.className, data.fields.map { (name, valueOpt) -> name to (valueOpt?.let { parse(it) } ?: XR.Const.Null(expr.loc)) }, expr.loc)
      },

      // parse lambda in a capture block
      case(Ir.FunctionExpression.withBlock[Is.Companion(), Is.Companion()]).thenThis { params, blockBody ->
        XR.FunctionN(params.map { it.makeIdent() }, parseFunctionBlockBody(blockBody), expr.loc)
      },

      case(Ir.Call.FunctionMemN[Ir.Expr.ClassOf<Function<*>>(), Is.Companion("invoke"), Is.Companion()]).thenThis { hostFunction, args ->
        XR.FunctionApply(parse(hostFunction), args.map { parse(it) }, expr.loc)
      },

      case(Ir.Call.FunctionMemN[Is.Companion(), Is.Companion.of("param", "paramCtx", "paramCustom"), Is.Companion()]).thenThis { _, args ->
        val paramValue = args.first()
        val paramBindType =
          when {
            this.ownerHasAnnotation<ParamStatic>() -> {
              val staticRef = this.symbol.owner.getAnnotationArgs<ParamStatic>().firstOrNull()?.let { param -> param as? IrClassReference
                ?: parseError("ParamStatic annotation must have a single argument that is a class-reference (e.g. PureFunction::class)", this)
              } ?: parseError("Could not find ParamStatic annotation", this)
              val classId = staticRef.classType.classId() ?: parseError("Could not find classId for ParamStatic annotation", this)
              ParamBind.Type.ParamStatic(classId)
            }
            this.ownerHasAnnotation<ParamCustom>() -> {
              // serializer should be the 2nd arg i.e. paramCustom(value, serializer)
              ParamBind.Type.ParamCustom(args.lastOrNull() ?: parseError("ParamCustom annotation must have a second argument that is a class-reference (e.g. PureFunction::class)", this), paramValue.type)
            }
            this.ownerHasAnnotation<ParamCustomValue>() -> {
              ParamBind.Type.ParamCustomValue(paramValue)
            }
            this.ownerHasAnnotation<ParamCtx>() -> {
              ParamBind.Type.ParamCtx(paramValue.type)
            }
            else -> parseError("Could not find Param annotation on the param function of the call", this)
          }

        val bid = BID.Companion.new()
        binds.addParam(bid, paramValue, paramBindType)
        XR.TagForParam(bid, XR.ParamType.Single, TypeParser.of(this), paramValue.loc)
      },

      case(Ir.Call.FunctionMemN[Is.Companion(), Is.Companion.of("params", "paramsCtx", "paramsCustom"), Is.Companion()]).thenThis { _, args ->
        val paramValue = args.first()
        val paramBindType =
          when {
            // currently not used because the specific ones have been commented out. Waiting for @SignatureName in KMP
            this.ownerHasAnnotation<ParamStatic>() -> {
              val staticRef = this.symbol.owner.getAnnotationArgs<ParamStatic>().firstOrNull()?.let { param -> param as? IrClassReference
                ?: parseError("ParamStatic annotation must have a single argument that is a class-reference (e.g. PureFunction::class)", this)
              } ?: parseError("Could not find ParamStatic annotation", this)
              val classId = staticRef.classType.classId() ?: parseError("Could not find classId for ParamStatic annotation", this)
              ParamBind.Type.ParamListStatic(classId)
            }
            this.ownerHasAnnotation<ParamPrimitive>() -> {
              val irType = this.typeArguments.firstOrNull() ?: parseError("params-call must have a single type argument", this)
              val paramSerializerClassId = getSerializerForType(irType)
                ?: parseError(
                  "Could not find primitive-serializer for type: ${irType.dumpKotlinLike()}. Primitive serializers are only defined for: Int, Long, Float, Double, String, Boolean, and the kotlinx LocalDate, LocalTime, LocalDateTime, and Instant",
                  this
                )
              ParamBind.Type.ParamListStatic(paramSerializerClassId)
            }
            this.ownerHasAnnotation<ParamCustom>() -> {
              // serializer should be the 2nd arg i.e. paramCustom(value, serializer)
              val irType = this.typeArguments.firstOrNull() ?: parseError("params-call must have a single type argument", this)
              ParamBind.Type.ParamListCustom(args.lastOrNull() ?: parseError("ParamCustom annotation must have a second argument that is a class-reference (e.g. PureFunction::class)", this), irType)
            }
            this.ownerHasAnnotation<ParamCustomValue>() -> {
              ParamBind.Type.ParamListCustomValue(paramValue)
            }
            this.ownerHasAnnotation<ParamCtx>() -> {
              val irType = this.typeArguments.firstOrNull() ?: parseError("params-call must have a single type argument", this)
              ParamBind.Type.ParamListCtx(irType)
            }
            else -> parseError("Could not find Param annotation on the params function of the call", this)
          }

        val bid = BID.Companion.new()
        binds.addParam(bid, paramValue, paramBindType)
        XR.TagForParam(bid, XR.ParamType.Multi, TypeParser.ofFirstArgOfReturnTypeOf(this), paramValue.loc)
      },

      case(Ir.Call.FunctionMem1[Ir.Expr.ClassOf<Params<*>>(), Is.Companion("contains"), Is.Companion()]).thenThis { head, params ->
        val cid = head.type.classId()?.toXR() ?: parseError("Could not find classId for the head of the contains call", head)
        XR.MethodCall(parse(head), "contains", listOf(parse(params)), XR.CallType.PureFunction, cid, XRType.Value, expr.loc)
      },

      case(Ir.Call.FunctionMem0[ExtractorsDomain.Call.InterpolateInvoke[Is.Companion()], Is.Companion.of("invoke", "asPure", "asConditon", "asPureConditon")]).thenThis { (components), _ ->
        val segs = components.map { Seg.parse(it) }
        val (partsRaw, paramsRaw) =
          UnzipPartsParams<Seg>({ it is Seg.Const }, { a, b -> (a.constOrFail()).mergeWith(a.constOrFail()) }, { Seg.Const("") })
            .invoke(segs)
        val parts = partsRaw.map { it.constOrFail().value }
        val paramsExprs = paramsRaw.map { it.exprOrFail() }
        val paramsIrs = paramsExprs.map { parse(it.expr) }
        when (this.funName) {
          "invoke" -> XR.Infix(parts, paramsIrs, false, false, TypeParser.of(this), expr.loc)
          "asPure" -> XR.Infix(parts, paramsIrs, true, false, TypeParser.of(this), expr.loc)
          "asConditon" -> XR.Infix(parts, paramsIrs, false, false, XRType.BooleanExpression, expr.loc)
          "asPureConditon" -> XR.Infix(parts, paramsIrs, true, false, XRType.BooleanExpression, expr.loc)
          else -> parseError("Unknown Interpolate function: ${this.funName}", expr)
        }
      },

      case(Ir.Call.FunctionMem0[Is.Companion(), Is.Companion("value")]).thenIf { useExpr, _ -> useExpr.type.isClass<SqlQuery<*>>() }.then { sqlQueryIr, _ ->
        XR.QueryToExpr(ParseQuery.parse(sqlQueryIr), sqlQueryIr.loc)
      },
      // Now the same for SqlExpression
      // TODO check that the extension reciever is Ir.Expr.ClassOf<SqlExpression<*>> (and the dispatch method is CapturedBlock)
      case(Ir.Call.FunctionMem0[Is.Companion(), Is.Companion("use")]).thenIf { useExpr, _ -> useExpr.type.isClass<SqlExpression<*>>() }.then { sqlExprIr, _ ->
        sqlExprIr.match(
          case(SqlExpressionExpr.Uprootable[Is.Companion()]).then { uprootable ->
            // Add all binds from the found SqlExpression instance, this will be truned into something like `currLifts + SqlExpression.lifts` late
            binds.addAllParams(sqlExprIr)
            // Then unpack and return the XR
            uprootable.xr
          }
          // TODO add dynamic case
        ) ?: run {
          val bid = BID.Companion.new()
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
      case(ExtractorsDomain.Call.`x op y`[Is.Companion()]).thenThis { opCall ->
        val (x, op, y) = opCall
        XR.BinaryOp(parse(x), op, parse(y), expr.loc)
      },
      // Unary Operators
      case(ExtractorsDomain.Call.`(op)x`[Is.Companion()]).thenThis { opCall ->
        val (x, op) = opCall
        XR.UnaryOp(op, parse(x), expr.loc)
      },

      case(ExtractorsDomain.Call.`x to y`[Is.Companion(), Is.Companion()]).thenThis { x, y ->
        XR.Product.Tuple(parse(x), parse(y), expr.loc)
      },

      // Other situations where you might have an identifier which is not an SqlVar e.g. with variable bindings in a Block (inside an expression)
      case(Ir.GetValue[Is.Companion()]).thenIfThis { this.isCapturedVariable() || this.isCapturedFunctionArgument() }.thenThis { sym ->
        XR.Ident(sym.safeName, TypeParser.of(this), this.locationXR()) // this.symbol.owner.type
      },
      case(Ir.Const[Is.Companion()]).thenThis {
        parseConst(this)
      },

      // TODO need to check for @SerialName("name_override") annotations from the Kotlin seriazation API and override the name
      //      (the parser also needs to be able to generated these based on a mapping)
      case(Ir.Call.Property[Is.Companion(), Is.Companion()]).then { expr, name ->
        XR.Property(parse(expr), name, XR.Visibility.Visible, expr.loc)
      },

      // case(Ir.Call.Function[Is()]).thenIf { (list) -> list.size == 2 }.thenThis { list ->
      //   val a = list.get(0)
      //   val b = list.get(1)
      //   // TODO need to check that's its actually a binary operator!!!
      //   XR.BinaryOp(parse(a), parseSymbol(this.symbol), parse(b))
      // }
      // ,
      case(Ir.Block[Is.Companion(), Is.Companion()]).then { stmts, ret ->
        XR.Block(stmts.map { parseBlockStatement(it) }, parse(ret), expr.loc)
      },
      case(Ir.When[Is.Companion()]).thenThis { cases ->
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



  private fun getSerializerForType(type: IrType): ClassId? =
    when {
      type.isString() -> classIdOf<ParamSerializer.String>()
      type.isChar() -> classIdOf<ParamSerializer.Char>()
      type.isInt() -> classIdOf<ParamSerializer.Int>()
      type.isShort() -> classIdOf<ParamSerializer.Short>()
      type.isLong() -> classIdOf<ParamSerializer.Long>()
      type.isFloat() -> classIdOf<ParamSerializer.Float>()
      type.isDouble() -> classIdOf<ParamSerializer.Double>()
      type.isBoolean() -> classIdOf<ParamSerializer.Boolean>()
      type.isClassStrict<LocalDate>() -> classIdOf<LocalDate>()
      type.isClassStrict<LocalTime>() -> classIdOf<LocalTime>()
      type.isClassStrict<LocalDateTime>() -> classIdOf<LocalDateTime>()
      else -> null
    }

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
      IrConstKind.Boolean -> XR.Const.Boolean(irConst.value as Boolean, irConst.loc)
      IrConstKind.Char -> XR.Const.Char(irConst.value as Char, irConst.loc)
      IrConstKind.Byte -> XR.Const.Byte(irConst.value as Int, irConst.loc)
      IrConstKind.Short -> XR.Const.Short(irConst.value as Short, irConst.loc)
      IrConstKind.Int -> XR.Const.Int(irConst.value as Int, irConst.loc)
      IrConstKind.Long -> XR.Const.Long(irConst.value as Long, irConst.loc)
      IrConstKind.String -> XR.Const.String(irConst.value as String, irConst.loc)
      IrConstKind.Float -> XR.Const.Float(irConst.value as Float, irConst.loc)
      IrConstKind.Double -> XR.Const.Double(irConst.value as Double, irConst.loc)
      else -> parseError("Unknown IrConstKind: ${irConst.kind}")
    }


}
