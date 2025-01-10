package io.exoquery.plugin.trees

import io.decomat.*
import io.exoquery.BID
import io.exoquery.Param
import io.exoquery.Params
import io.exoquery.Runtimes
import io.exoquery.plugin.classIdOf
import io.exoquery.plugin.logging.CompileLogger
import io.exoquery.plugin.printing.dumpSimple
import io.exoquery.plugin.transform.*
import io.exoquery.xr.XR
import io.exoquery.xr.encode
import kotlinx.serialization.decodeFromHexString
import kotlinx.serialization.protobuf.ProtoBuf
import org.jetbrains.kotlin.ir.backend.js.utils.valueArguments
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irGetObjectValue
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.getConstructorTypeArgument
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

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
        .map { it.callDispatch("runtimes")() }
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
        .map { it.callDispatch("params")() }
        .fold(newParams, { acc, nextParams ->
          newParams.callDispatch("plus")(nextParams)
        })
    }
  }
}

context(BuilderContext, CompileLogger) private fun RuntimeEmpty() = run {
  //val runtimesCompanionRef = builder.irGetObjectValue(pluginCtx.referenceClass(classIdOf<io.exoquery.Runtimes.Companion>())!!)
  val cls = ClassId.topLevel(FqName("io.exoquery.Runtimes"))
  val clsSym = pluginCtx.referenceClass(cls) ?: throw RuntimeException("Could not find the reference for the class $cls in the context")
  val clsSymCompanion = clsSym.owner.companionObject() ?: throw RuntimeException("Could not find the companion object for the class $cls")
  val runtimesCompanionRef = builder.irGetObject(clsSymCompanion.symbol)
  runtimesCompanionRef.callDispatch("Empty")()
}

object SqlExpressionExpr {
  data class Uprootable(val packedXR: String) {
    // This is an expensive operation so put it behind a lazy value that the user will invoke only if needed
    val xr by lazy { ProtoBuf.decodeFromHexString<XR.Expression>(packedXR) }

    // re-create the SqlExpression instance. Note that we need a varaible from which to take params
    // So for example say we have something like:
    // val x = capture { 123 } // i.e. SqlExpression(unpack(xyz), params=...)
    // val y = x
    // we want to change `val y` to:
    // val y = SqlExpression(unpack(xyz), lifts=x.lifts)
    // That is because as we propagate the SqlExpression instance we need to keep the params the same
    // but the varaibles referenced in the params might refer to local things that are no longer
    // avaiable as well keep inlining the SqlExpression instances
    context(BuilderContext, CompileLogger)
    fun replant(paramsFrom: IrExpression): IrExpression {
      val strExpr = call("io.exoquery.unpackExpr").invoke(builder.irString(packedXR))
      // TODO we know at this point Runtimes is Runtimes.Empty so need to add that when we add that variable
      //error(
      //  """|------- Calling replant -------
      //     |${Caller.Dispatch(paramsFrom).call("params")().dump()}
      //""".trimMargin())

      val callParams = paramsFrom.callDispatch("params").invoke()

      // Not sure why calling it like this makes it blow up
      //val make = makeClassFromString("io.exoquery.SqlExpression", listOf(strExpr, Caller.Dispatch(paramsFrom).call("params")()))
      val make = makeClassFromString("io.exoquery.SqlExpression", listOf(strExpr, RuntimeEmpty(), callParams))
      return make
    }

    companion object {
      context (CompileLogger) operator fun <AP: Pattern<Uprootable>> get(x: AP) =
        customPattern1(x) { it: IrExpression ->
          it.match(
            // Match on: SqlExpression(unpackExpr(str))
            case(ExtractorsDomain.CaseClassConstructorCall1Plus[Is("io.exoquery.SqlExpression"), Ir.Call.FunctionUntethered1[Is("unpackExpr"), Is()]])
              .thenIf { _, _ ->
                // Check that the 2nd argument to SqlExpression is Runtimes.Empty i.e. SqlExpression(xr=unpackExpr(str), runtimes=Runtimes.Empty, ...)
                comp.valueArguments[1].match(
                  case(Ir.Call.FunctionMem0[Ir.Type.ClassOf<io.exoquery.Runtimes.Companion>(), Is("Empty")]).then { expr, _ -> true }
                ) ?: false
              }
              .then { _, (_, irStr) ->
                // The 1st argument to SqlExpression in the unpackExpr ie. SqlExpression(unpackExpr(str), ...)
                val constPackedXR = irStr as? IrConst ?: throw IllegalArgumentException("value passed to unpackExpr was not a constant-string in:\n${it.dumpKotlinLike()}")
                Components1(Uprootable(constPackedXR.value.toString()))
              }
          )
        }

      context(BuilderContext, CompileLogger) fun plantNewUprootable(xr: XR.Expression, params: ParamsExpr): IrExpression {
        val packedXR = xr.encode()
        val strExpr = call("io.exoquery.unpackExpr").invoke(builder.irString(packedXR))
        val make = makeClassFromString("io.exoquery.SqlExpression", listOf(strExpr, RuntimeEmpty(), params.lift()))
        return make
      }

      context(BuilderContext, CompileLogger) fun plantNewPluckable(xr: XR.Expression, runtimes: RuntimesExpr, params: ParamsExpr): IrExpression {
        val packedXR = xr.encode()
        val strExpr = call("io.exoquery.unpackExpr").invoke(builder.irString(packedXR))
        val make = makeClassFromString("io.exoquery.SqlExpression", listOf(strExpr, runtimes.lift(), params.lift()))
        return make
      }
    }
  }
}

// This is effectively a carbon-copy of the SqlExpressionExpr with some different names. We can abstract out many
// common pieces of this code into a shared Base-class or shared functions but for only two ContainerOfXR types it is
// not worth it. Need to keep an eye on this as we add more ContainerOfXR types
object SqlQueryExpr {
  data class Uprootable(val packedXR: String) {
    val xr by lazy { ProtoBuf.decodeFromHexString<XR.Query>(packedXR) }
    context(BuilderContext, CompileLogger)
    fun replant(paramsFrom: IrExpression): IrExpression {
      val strExpr = call("io.exoquery.unpackQuery").invoke(builder.irString(packedXR))
      val callParams = paramsFrom.callDispatch("params").invoke()
      val make = makeClassFromString("io.exoquery.SqlQuery", listOf(strExpr, RuntimeEmpty(), callParams))
      return make
    }

    companion object {
      context (CompileLogger) operator fun <AP: Pattern<Uprootable>> get(x: AP) =
        customPattern1(x) { it: IrExpression ->
          it.match(
            case(ExtractorsDomain.CaseClassConstructorCall1Plus[Is("io.exoquery.SqlQuery"), Ir.Call.FunctionUntethered1[Is("unpackQuery"), Is()]])
              .thenIf { _, _ ->
                comp.valueArguments[1].match(
                  case(Ir.Call.FunctionMem0[Ir.Type.ClassOf<io.exoquery.Runtimes.Companion>(), Is("Empty")]).then { expr, _ -> true }
                ) ?: false
              }
              .then { _, (_, irStr) ->
                val constPackedXR = irStr as? IrConst ?: throw IllegalArgumentException("value passed to unpackQuery was not a constant-string in:\n${it.dumpKotlinLike()}")
                Components1(Uprootable(constPackedXR.value.toString()))
              }
          )
        }

      context(BuilderContext, CompileLogger) fun plantNewUprootable(xr: XR.Query, params: ParamsExpr): IrExpression {
        val packedXR = xr.encode()
        val strExpr = call("io.exoquery.unpackQuery").invoke(builder.irString(packedXR))
        val make = makeClassFromString("io.exoquery.SqlQuery", listOf(strExpr, RuntimeEmpty(), params.lift()))
        return make
      }

      context(BuilderContext, CompileLogger) fun plantNewPluckable(xr: XR.Query, runtimes: RuntimesExpr, params: ParamsExpr): IrExpression {
        val packedXR = xr.encode()
        val strExpr = call("io.exoquery.unpackQuery").invoke(builder.irString(packedXR))
        val make = makeClassFromString("io.exoquery.SqlQuery", listOf(strExpr, runtimes.lift(), params.lift()))
        return make
      }
    }
  }
}
