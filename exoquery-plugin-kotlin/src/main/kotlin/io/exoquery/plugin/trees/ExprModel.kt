package io.exoquery.plugin.trees

import io.decomat.*
import io.exoquery.BID
import io.exoquery.Runtimes
import io.exoquery.plugin.logging.CompileLogger
import io.exoquery.plugin.transform.BuilderContext
import io.exoquery.plugin.transform.call
import io.exoquery.xr.XR
import kotlinx.serialization.decodeFromHexString
import kotlinx.serialization.protobuf.ProtoBuf
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.util.dumpKotlinLike

class RuntimesExpr(val runtimes: List<Pair<BID, IrExpression>>) {
  context(BuilderContext) fun lift(): IrExpression {
    return with (makeLifter()) {
      val bindsList = runtimes.map { pair ->
        pair.lift(
          {bid -> bid.lift()},
          { it })
      }
      make<Runtimes>(bindsList.liftExpr<Pair<BID, IrExpression>>())
    }
  }
}

object SqlExpressionExpr {
  data class Uprootable(val packedXR: String) {
    // This is an expensive operation so put it behind a lazy value that the user will invoke only if needed
    val xr by lazy { ProtoBuf.decodeFromHexString<XR.Expression>(packedXR) }

    context(ParserContext, BuilderContext, CompileLogger)
    fun replant(): IrExpression {
      val strExpr = call("io.exoquery.unpackExpr").invoke(builder.irString(packedXR))
      val make = makeClassFromString("io.exoquery.SqlExpression", listOf(strExpr))
      return make
    }

    companion object {
      context (CompileLogger) operator fun <AP: Pattern<Uprootable>> get(x: AP) =
        customPattern1(x) { it: IrExpression ->
          it.match(
            // Match on: SqlExpression(unpackExpr(str))
            case(ExtractorsDomain.CaseClassConstructorCall1[Is("io.exoquery.SqlExpression"), Ir.Call.FunctionUntethered1[Is()]]).then { _, (irStr) ->
              val constPackedXR = irStr as? IrConst<String> ?: throw IllegalArgumentException("value passed to unpackExpr was not a constant-string in:\n${it.dumpKotlinLike()}")
              Components1(Uprootable(constPackedXR.value))
            }
          )
        }
    }
  } // TODO need add lifts
}

// create an IrExpression DynamicBind(listOf(Pair(BID, RuntimeBindValue), etc...))
