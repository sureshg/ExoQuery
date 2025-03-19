package io.exoquery.plugin.trees

import io.decomat.*
import io.exoquery.BID
import io.exoquery.Param
import io.exoquery.ParamBatchRefiner
import io.exoquery.ParamMulti
import io.exoquery.ParamSingle
import io.exoquery.ParamSet
import io.exoquery.RuntimeSet
import io.exoquery.parseError
import io.exoquery.plugin.classId
import io.exoquery.plugin.classIdOf
import io.exoquery.plugin.transform.*
import io.exoquery.xr.EncodingXR
import io.exoquery.xr.XR
import io.exoquery.xr.encode
import kotlinx.serialization.decodeFromHexString
import org.jetbrains.kotlin.ir.backend.js.utils.valueArguments
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.isPrimitiveType
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.name.ClassId

class RuntimesExpr(val runtimes: List<Pair<BID, IrExpression>>, val runtimesToCompose: List<IrExpression>) {
  context (CX.Scope, CX.Builder) fun lift(): IrExpression {
    return with (makeLifter()) {
      val bindsList = runtimes.map { pair ->
        pair.lift(
          {bid -> bid.lift()},
          { it })
      }
      val newRuntimes: IrExpression = make<RuntimeSet>(bindsList.liftExpr<Pair<BID, IrExpression>>())
      runtimesToCompose
        // First take the .runtimes property from each SqlExpression instance
        .map { it.callDispatch("runtimes")() }
        // Then compose them them together with the new lifts
        .fold(newRuntimes, { acc, nextRuntimes ->
          acc.callDispatch("plus")(nextRuntimes)
        })
    }
  }
}

/** Represents the data coming from the `param` function in the DSL on the backend */
data class ParamBind(val bid: BID, val value: IrExpression, val paramSerializer: ParamBind.Type) {
  /** Constructors corresponding to needed ingredients of the annotations:
   * ```
   * annotation class ParamStatic(val type: KClass<out ParamSerializer<out Any>>)
   * annotation class ParamCtx
   * annotation class ParamCustom
   * annotation class ParamCustomValue
   * ```
   */
  sealed interface Type {
    sealed interface Single: Type {
      context (CX.Scope, CX.Builder) fun makeSerializer(): IrExpression
      context (CX.Scope, CX.Builder) fun makeValue(originalValue: IrExpression): IrExpression = originalValue
    }

    companion object {
      context(CX.Scope)
      fun auto(expr: IrExpression) = run {
        //logger.error("--------------- Process Paramter ${expr.dumpKotlinLike()} for type: ${expr.type.classId()}")
        if (expr.type.isPrimitiveType())
          ParamStatic(getSerializerForType(expr.type) ?: parseError("Could not create a primitive-type parser ofr the type ${expr.type.dumpKotlinLike()} whose class-id is ${expr.type.classId()}", expr))
          //ParamStatic(expr.type.classId()!!)
        else
          ParamCtx(expr.type)
      }
    }

    context (CX.Scope, CX.Builder) fun build(bid: BID, originalValue: IrExpression, lifter: Lifter): IrExpression

    data class ParamUsingBatchAlias(val batchVariable: IrValueParameter, val param: Type.Single): Type {
      context (CX.Scope, CX.Builder) override fun build(bid: BID, originalValue: IrExpression, lifter: Lifter) =
        with (lifter) {
          val refinerLambda = createLambda1(param.makeValue(originalValue), batchVariable, currentDeclarationParentOrFail())
          make<ParamBatchRefiner<*, *>>(bid.lift(), refinerLambda, param.makeSerializer())
        }
    }

    /**
     * This comes out of `param(value: String/Char/Int/Short/...)` serializers.
     * classId is the classId of the static ParamSerializer object
     */
    data class ParamStatic(val classId: ClassId) : Type, Single {
      context (CX.Scope, CX.Builder) override fun makeSerializer() = makeObjectFromId(classId)
      context (CX.Scope, CX.Builder) override fun build(bid: BID, originalValue: IrExpression, lifter: Lifter) =
        with (lifter) {
          make<ParamSingle<*>>(bid.lift(), makeValue(originalValue), makeSerializer())
        }
    }
    /**
     * This comes out of paramCtx(value: T)
     * `type` is the type to use when calling io.exoquery.serial.contextualSerializer()
     * originalValue is the `value` arg
     */
    data class ParamCtx(val type: IrType) : Type, Single {
      context (CX.Scope, CX.Builder) override fun makeSerializer() = callWithParams("io.exoquery.serial", "contextualSerializer", listOf(type)).invoke()
      context (CX.Scope, CX.Builder) override fun makeValue(originalValue: IrExpression) = originalValue
      context (CX.Scope, CX.Builder) override fun build(bid: BID, originalValue: IrExpression, lifter: Lifter) =
        with (lifter) {
          make<ParamSingle<*>>(bid.lift(), makeValue(originalValue), makeSerializer())
        }
    }
    /**
     * This comes out of paramCustom(value: T, serializer: SerializationStrategy<T>)
     * ktSerializer is the SerializationStrategy (serializer arg) instance to be passed into ParamSerializer.Custom,
     * which is created via `io.exoquery.serial.customSerializer` call below
     * complete with the serializer to pass in and the type to use when calling it.
     * originalValue is `value: T`.
     */
    data class ParamCustom(val ktSerializer: IrExpression, val type: IrType): Type, Single {
      context (CX.Scope, CX.Builder) override fun makeSerializer() = callWithParams("io.exoquery.serial", "customSerializer", listOf(type)).invoke(ktSerializer)
      context (CX.Scope, CX.Builder) override fun build(bid: BID, originalValue: IrExpression, lifter: Lifter) =
        with (lifter) {
          make<ParamSingle<*>>(bid.lift(), makeValue(originalValue), makeSerializer())
        }
    }
    /** valueWithSet is the ValueWithSerializer instance passed to param */
    data class ParamCustomValue(val valueWithSerializer: IrExpression): Type, Single {
      context (CX.Scope, CX.Builder) override fun makeSerializer() = constructValueSerializer(valueWithSerializer)
      context (CX.Scope, CX.Builder) override fun makeValue(originalValue: IrExpression) = originalValue.callDispatch("value")()
      context (CX.Scope, CX.Builder) override fun build(bid: BID, originalValue: IrExpression, lifter: Lifter) = run {
        with (lifter) {
          make<ParamSingle<*>>(bid.lift(), makeValue(originalValue), makeSerializer())
        }
      }
    }

    context (CX.Scope, CX.Builder)
    fun constructValueSerializer(valueWithSerializer: IrExpression) = run {
      // Get the type-argument of the ValueWithSerializer<T> instance which is the actual type to be serialized e.g. ValueWithSerializer<MyCustomDateType>
      val type = valueWithSerializer.type.simpleTypeArgs.firstOrNull() ?: parseError("Could not get the type of the ValueWithSerializer instance", valueWithSerializer)
      val constructSerializer = callWithParams("io.exoquery.serial", "customValueSerializer", listOf(type)).invoke(valueWithSerializer)
      constructSerializer
    }


    /**
     * for params(listOf(...))
     * originalValues is the list
     */
    data class ParamListStatic(val classId: ClassId) : Type {
      context (CX.Scope, CX.Builder) override fun build(bid: BID, originalValues: IrExpression, lifter: Lifter) =
        with (lifter) {
          make<ParamMulti<*>>(bid.lift(), originalValues, makeObjectFromId(classId))
        }
    }
    data class ParamListCtx(val type: IrType) : Type {
      context (CX.Scope, CX.Builder) override fun build(bid: BID, originalValues: IrExpression, lifter: Lifter) =
        with (lifter) {
          make<ParamMulti<*>>(bid.lift(), originalValues, callWithParams("io.exoquery.serial", "contextualSerializer", listOf(type)).invoke())
        }
    }
    /**
     * Comes out of paramsCustom(values: List<T>, serializer: SerializationStrategy<T>)
     * ktSerializer is the SerializationStrategy (serializer arg) instance to be passed into ParamSerializer.Custom,
     * which is created via `io.exoquery.serial.customSerializer` below. The type is the type to use when calling it.
     * originalValues is the `values` arg of the params call above.
     */
    data class ParamListCustom(val ktSerializer: IrExpression, val type: IrType): Type {
      context (CX.Scope, CX.Builder) override fun build(bid: BID, originalValues: IrExpression, lifter: Lifter) =
        with(lifter) {
          make<ParamMulti<*>>(bid.lift(), originalValues, callWithParams("io.exoquery.serial", "customSerializer", listOf(type)).invoke(ktSerializer))
        }
    }
    data class ParamListCustomValue(val valueWithSerializer: IrExpression): Type {
      context (CX.Scope, CX.Builder) override fun build(bid: BID, originalValue: IrExpression, lifter: Lifter) = run {
        val paramSerializer = constructValueListSerializer(valueWithSerializer)
        with (lifter) {
          make<ParamMulti<*>>(bid.lift(), originalValue.callDispatch("values")(), paramSerializer)
        }
      }
    }

    context (CX.Scope, CX.Builder)
    fun constructValueListSerializer(valueListWithSerializer: IrExpression) = run {
      // Get the type-argument of the ValuesWithSerializer<T> instance which is the actual type to be serialized e.g. ValueWithSerializer<MyCustomDateType>
      val type = valueListWithSerializer.type.simpleTypeArgs.firstOrNull() ?: parseError("Could not get the type of the ValuesWithSerializer instance", valueListWithSerializer)
      val constructSerializer = callWithParams("io.exoquery.serial", "customValueListSerializer", listOf(type)).invoke(valueListWithSerializer)
      constructSerializer
    }
  }
}

class ParamsExpr(val paramBinds: List<ParamBind>, val paramsToCompose: List<IrExpression>) {
  context (CX.Scope, CX.Builder) fun lift(): IrExpression {
    val lifter = makeLifter()
    return with (lifter) {
      val paramsList = paramBinds.map { (bid, value, paramType) ->
        paramType.build(bid, value, lifter)
      }
      val newParams: IrExpression = make<ParamSet>(paramsList.liftExpr<Param<*>>())
      val out =
      paramsToCompose
        .map { it.callDispatch("params")() }
        .fold(newParams, { acc, nextParams ->
          acc.callDispatch("plus")(nextParams)
        })
      out
    }
  }
}

context(CX.Scope, CX.Builder) private fun RuntimeEmpty(): IrExpression {
  //val runtimesCompanionRef = builder.irGetObjectValue(pluginCtx.referenceClass(classIdOf<io.exoquery.Runtimes.Companion>())!!)
  val cls = classIdOf<RuntimeSet>() ?: throw RuntimeException("Could not find the class-id for RuntimeSet")
  val clsSym = pluginCtx.referenceClass(cls) ?: throw RuntimeException("Could not find the reference for the class $cls in the context")
  val clsSymCompanion = clsSym.owner.companionObject() ?: throw RuntimeException("Could not find the companion object for the class $cls")
  val runtimesCompanionRef = builder.irGetObject(clsSymCompanion.symbol)
  return runtimesCompanionRef.callDispatch("Empty")()
}

object SqlExpressionExpr {
  data class Uprootable(val packedXR: String) {
    // This is an expensive operation so put it behind a lazy value that the user will invoke only if needed
    val xr by lazy { EncodingXR.protoBuf.decodeFromHexString<XR.Expression>(packedXR) }

    // re-create the SqlExpression instance. Note that we need a varaible from which to take params
    // So for example say we have something like:
    // val x = capture { 123 } // i.e. SqlExpression(unpack(xyz), params=...)
    // val y = x
    // we want to change `val y` to:
    // val y = SqlExpression(unpack(xyz), lifts=x.lifts)
    // That is because as we propagate the SqlExpression instance we need to keep the params the same
    // but the varaibles referenced in the params might refer to local things that are no longer
    // avaiable as well keep inlining the SqlExpression instances
    context(CX.Scope, CX.Builder)
    fun replant(paramsFrom: IrExpression): IrExpression {
      val strExpr = call(PT.io_exoquery_unpackExpr).invoke(builder.irString(packedXR))
      // TODO we know at this point Runtimes is Runtimes.Empty so need to add that when we add that variable
      //error(
      //  """|------- Calling replant -------
      //     |${Caller.Dispatch(paramsFrom).call("params")().dump()}
      //""".trimMargin())

      val callParams = paramsFrom.callDispatch("params").invoke()

      // Not sure why calling it like this makes it blow up
      //val make = makeClassFromString(Paths.SqlExpression, listOf(strExpr, Caller.Dispatch(paramsFrom).call("params")()))
      val make = makeClassFromString(PT.io_exoquery_SqlExpression, listOf(strExpr, RuntimeEmpty(), callParams))
      return make
    }

    companion object {
      context (CX.Scope) operator fun <AP: Pattern<Uprootable>> get(x: AP) =
        customPattern1("SqlExpressionExpr.Uprootable", x) { it: IrExpression ->
          it.match(
            // Match on: SqlExpression(unpackExpr(str))
            case(ExtractorsDomain.CaseClassConstructorCall1Plus[Is(PT.io_exoquery_SqlExpression), Ir.Call.FunctionUntethered1[Is(PT.io_exoquery_unpackExpr), Is()]])
              .thenIf { _, _ ->
                // Check that the 2nd argument to SqlExpression is Runtimes.Empty i.e. SqlExpression(xr=unpackExpr(str), runtimes=Runtimes.Empty, ...)
                comp.valueArguments[1].isEmptyRuntimes()
              }
              .then { _, (_, irStr) ->
                // The 1st argument to SqlExpression in the unpackExpr ie. SqlExpression(unpackExpr(str), ...)
                val constPackedXR = irStr as? IrConst ?: throw IllegalArgumentException("value passed to unpackExpr was not a constant-string in:\n${it.dumpKotlinLike()}")
                Components1(Uprootable(constPackedXR.value.toString()))
              }
          )
        }

      context(CX.Scope, CX.Builder) fun plantNewUprootable(xr: XR.Expression, params: ParamsExpr): IrExpression {
        val packedXR = xr.encode()
        val strExpr = call(PT.io_exoquery_unpackExpr).invoke(builder.irString(packedXR))
        val make = makeClassFromString(PT.io_exoquery_SqlExpression, listOf(strExpr, RuntimeEmpty(), params.lift()))
        return make
      }

      context(CX.Scope, CX.Builder) fun plantNewPluckable(xr: XR.Expression, runtimes: RuntimesExpr, params: ParamsExpr): IrExpression {
        val packedXR = xr.encode()
        val strExpr = call(PT.io_exoquery_unpackExpr).invoke(builder.irString(packedXR))
        val make = makeClassFromString(PT.io_exoquery_SqlExpression, listOf(strExpr, runtimes.lift(), params.lift()))
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
    val xr by lazy {
      try {
        EncodingXR.protoBuf.decodeFromHexString<XR.Query>(packedXR)
      } catch (e: Throwable) {
        throw IllegalArgumentException("Could not decode the XR.Query from the packed string: $packedXR", e)
      }
    }
    context(CX.Scope, CX.Builder)
    fun replant(paramsFrom: IrExpression): IrExpression {
      val strExpr = call(PT.io_exoquery_unpackQuery).invoke(builder.irString(packedXR))
      val callParams = paramsFrom.callDispatch("params").invoke()
      val make = makeClassFromString(PT.io_exoquery_SqlQuery, listOf(strExpr, RuntimeEmpty(), callParams))
      return make
    }

    companion object {
      context (CX.Scope) operator fun <AP: Pattern<Uprootable>> get(x: AP) =
        customPattern1("SqlQueryExpr.Uprootable", x) { it: IrExpression ->
          it.match(
            case(ExtractorsDomain.CaseClassConstructorCall1Plus[Is(PT.io_exoquery_SqlQuery), Ir.Call.FunctionUntethered1[Is(PT.io_exoquery_unpackQuery), Is()]])
              .thenIf { _, _ ->
                comp.valueArguments[1].isEmptyRuntimes()
              }
              .then { _, (_, irStr) ->
                val constPackedXR = irStr as? IrConst ?: throw IllegalArgumentException("value passed to unpackQuery was not a constant-string in:\n${it.dumpKotlinLike()}")
                Components1(Uprootable(constPackedXR.value.toString()))
              }
          )
        }

      context(CX.Scope, CX.Builder) fun plantNewUprootable(xr: XR.Query, params: ParamsExpr): IrExpression {
        val packedXR = xr.encode()
        val strExpr = call(PT.io_exoquery_unpackQuery).invoke(builder.irString(packedXR))
        val make = makeClassFromString(PT.io_exoquery_SqlQuery, listOf(strExpr, RuntimeEmpty(), params.lift()))
        return make
      }

      context(CX.Scope, CX.Builder) fun plantNewPluckable(xr: XR.Query, runtimes: RuntimesExpr, params: ParamsExpr): IrExpression {
        val packedXR = xr.encode()
        val strExpr = call(PT.io_exoquery_unpackQuery).invoke(builder.irString(packedXR))
        val make = makeClassFromString(PT.io_exoquery_SqlQuery, listOf(strExpr, runtimes.lift(), params.lift()))
        return make
      }
    }
  }
}


object SqlActionExpr {
  data class Uprootable(val packedXR: String) {
    val xr by lazy {
      try {
        EncodingXR.protoBuf.decodeFromHexString<XR.Action>(packedXR)
      } catch (e: Throwable) {
        throw IllegalArgumentException("Could not decode the XR.Action from the packed string: $packedXR", e)
      }
    }

    context(CX.Scope, CX.Builder)
    fun replant(paramsFrom: IrExpression): IrExpression {
      val strExpr = call(PT.io_exoquery_unpackAction).invoke(builder.irString(packedXR))
      val callParams = paramsFrom.callDispatch("params").invoke()
      val make = makeClassFromString(PT.io_exoquery_SqlAction, listOf(strExpr, RuntimeEmpty(), callParams))
      return make
    }

    companion object {
      context (CX.Scope) operator fun <AP : Pattern<Uprootable>> get(x: AP) =
        customPattern1("SqlActionExpr.Uprootable", x) { it: IrExpression ->
          it.match(
            case(ExtractorsDomain.CaseClassConstructorCall1Plus[Is(PT.io_exoquery_SqlAction), Ir.Call.FunctionUntethered1[Is(PT.io_exoquery_unpackAction), Is()]])
              .thenIf { _, _ -> comp.valueArguments[1].isEmptyRuntimes() }
              .then { _, (_, irStr) ->
                val constPackedXR = irStr as? IrConst ?: throw IllegalArgumentException("value passed to unpackAction was not a constant-string in:\n${it.dumpKotlinLike()}")
                Components1(Uprootable(constPackedXR.value.toString()))
              }
          )
        }

      context(CX.Scope, CX.Builder) fun plantNewUprootable(xr: XR.Action, params: ParamsExpr): IrExpression {
        val packedXR = xr.encode()
        val strExpr = call(PT.io_exoquery_unpackAction).invoke(builder.irString(packedXR))
        val make = makeClassFromString(PT.io_exoquery_SqlAction, listOf(strExpr, RuntimeEmpty(), params.lift()))
        return make
      }

      context(CX.Scope, CX.Builder) fun plantNewPluckable(xr: XR.Action, runtimes: RuntimesExpr, params: ParamsExpr): IrExpression {
        val packedXR = xr.encode()
        val strExpr = call(PT.io_exoquery_unpackAction).invoke(builder.irString(packedXR))
        val make = makeClassFromString(PT.io_exoquery_SqlAction, listOf(strExpr, runtimes.lift(), params.lift()))
        return make
      }
    }
  }
}

object SqlBatchActionExpr {
  data class Uprootable(val packedXR: String) {
    val xr by lazy {
      try {
        EncodingXR.protoBuf.decodeFromHexString<XR.Batching>(packedXR)
      } catch (e: Throwable) {
        throw IllegalArgumentException("Could not decode the XR.Batching from the packed string: $packedXR", e)
      }
    }

    context(CX.Scope, CX.Builder)
    fun replant(paramsFrom: IrExpression): IrExpression {
      val strExpr = call(PT.io_exoquery_unpackBatchAction).invoke(builder.irString(packedXR))
      val callParams = paramsFrom.callDispatch("params").invoke()
      val make = makeClassFromString(PT.io_exoquery_SqlBatchAction, listOf(strExpr, RuntimeEmpty(), callParams))
      return make
    }

    companion object {
      context (CX.Scope) operator fun <AP : Pattern<Uprootable>> get(x: AP) =
        customPattern1("SqlBatchActionExpr.Uprootable", x) { it: IrExpression ->
          it.match(
            case(ExtractorsDomain.CaseClassConstructorCall1Plus[Is(PT.io_exoquery_SqlBatchAction), Ir.Call.FunctionUntethered1[Is(PT.io_exoquery_unpackBatchAction), Is()]])
              .thenIf { _, _ -> comp.valueArguments[1].isEmptyRuntimes() }
              .then { _, (_, irStr) ->
                val constPackedXR = irStr as? IrConst ?: throw IllegalArgumentException("value passed to unpackBatchAction was not a constant-string in:\n${it.dumpKotlinLike()}")
                Components1(Uprootable(constPackedXR.value.toString()))
              }
          )
        }

      context(CX.Scope, CX.Builder) fun plantNewUprootable(xr: XR.Batching, batchParam: IrExpression, params: ParamsExpr): IrExpression {
        val packedXR = xr.encode()
        val strExpr = call(PT.io_exoquery_unpackBatchAction).invoke(builder.irString(packedXR))
        val make = makeClassFromString(PT.io_exoquery_SqlBatchAction, listOf(strExpr, RuntimeEmpty(), params.lift()))
        return make
      }
    }
  }
}


context(CX.Scope)
private fun IrExpression?.isEmptyRuntimes(): Boolean =
  this?.match(
    case(Ir.Call.FunctionMem0[Ir.Expr.ClassOf<RuntimeSet.Companion>(), Is(PT.EmptyRuntimes)]).then { _, _ -> true }
  ) ?: false
