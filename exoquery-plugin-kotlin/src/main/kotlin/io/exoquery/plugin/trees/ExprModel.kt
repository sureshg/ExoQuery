package io.exoquery.plugin.trees

import io.decomat.*
import io.exoquery.BID
import io.exoquery.Param
import io.exoquery.Params
import io.exoquery.Runtimes
import io.exoquery.plugin.logging.CompileLogger
import io.exoquery.plugin.printing.dumpSimple
import io.exoquery.plugin.transform.*
import io.exoquery.xr.XR
import kotlinx.serialization.decodeFromHexString
import kotlinx.serialization.protobuf.ProtoBuf
import org.jetbrains.kotlin.ir.backend.js.utils.valueArguments
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.dumpKotlinLike

class RuntimesExpr(val runtimes: List<Pair<BID, IrExpression>>, val runtimesToCompose: List<IrExpression>) {
  context(BuilderContext) fun lift(): IrExpression {
    return with (makeLifter()) {
      val bindsList = runtimes.map { pair ->
        pair.lift(
          {bid -> bid.lift()},
          { it })
      }
      val newRuntimes: IrExpression = make<Runtimes>(bindsList.liftExpr<Pair<BID, IrExpression>>())
      runtimesToCompose
        // First take the .runtimes property from each SqlExpression instance
        .map { it.callDispatch("runtimesInternal")() }
        // Then compose them them together with the new lifts
        .fold(newRuntimes, { acc, nextRuntimes ->
          newRuntimes.callDispatch("plus")(nextRuntimes)
        })
    }
  }
}

class ParamsExpr(val paramBinds: List<Pair<BID, IrExpression>>, val paramsToCompose: List<IrExpression>) {
  context(BuilderContext) fun lift(): IrExpression {
    return with (makeLifter()) {
      val paramsList = paramBinds.map { (bid, value) ->
        make<Param<*>>(bid.lift(), value)
      }
      val newParams: IrExpression = make<Params>(paramsList.liftExpr<Param<*>>())
      paramsToCompose
        .map { it.callDispatch("paramsInternal")() }
        .fold(newParams, { acc, nextParams ->
          Caller.Dispatch(newParams).call("plus")(nextParams)
        })
    }
  }
}

object SqlExpressionExpr {
  data class Uprootable(val packedXR: String) {
    // This is an expensive operation so put it behind a lazy value that the user will invoke only if needed
    val xr by lazy { ProtoBuf.decodeFromHexString<XR.Expression>(packedXR) }

    context(ParserContext, BuilderContext, CompileLogger)

    // re-create the SqlExpression instance. Note that we need a varaible from which to take params
    // So for example say we have something like:
    // val x = capture { 123 } // i.e. SqlExpression(unpack(xyz), params=...)
    // val y = x
    // we want to change `val y` to:
    // val y = SqlExpression(unpack(xyz), lifts=x.lifts)
    // That is because as we propagate the SqlExpression instance we need to keep the params the same
    // but the varaibles referenced in the params might refer to local things that are no longer
    // avaiable as well keep inlining the SqlExpression instances
    fun replant(paramsFrom: IrExpression): IrExpression {
      val strExpr = call("io.exoquery.unpackExpr").invoke(builder.irString(packedXR))
      // TODO we know at this point Runtimes is Runtimes.Empty so need to add that when we add that variable
      //error(
      //  """|------- Calling replant -------
      //     |${Caller.Dispatch(paramsFrom).call("params")().dump()}
      //""".trimMargin())

      val callParams = Caller.Dispatch(paramsFrom).call("paramsInternal").invoke()

      // Not sure why calling it like this makes it blow up
      //val make = makeClassFromString("io.exoquery.SqlExpression", listOf(strExpr, Caller.Dispatch(paramsFrom).call("params")()))
      val make = makeClassFromString("io.exoquery.SqlExpression", listOf(strExpr, callParams))
      return make
    }

    companion object {
      context (CompileLogger) operator fun <AP: Pattern<Uprootable>> get(x: AP) =
        customPattern1(x) { it: IrExpression ->
          it.match(
            // Match on: SqlExpression(unpackExpr(str))
            case(ExtractorsDomain.CaseClassConstructorCall1Plus[Is("io.exoquery.SqlExpression"), Ir.Call.FunctionUntethered1[Is("unpackExpr"), Is()]])
              // TODO thenIf need to check that Runtimes is Runtimes.Empty
              .then { _, (_, irStr) ->
                // The 1st argument to SqlExpression in the unpackExpr ie. SqlExpression(unpackExpr(str), ...)
                val constPackedXR = irStr as? IrConst<String> ?: throw IllegalArgumentException("value passed to unpackExpr was not a constant-string in:\n${it.dumpKotlinLike()}")
                Components1(Uprootable(constPackedXR.value))
              }
          )
        }
    }
  } // TODO need add lifts
}

// create an IrExpression DynamicBind(listOf(Pair(BID, RuntimeBindValue), etc...))
