package io.exoquery.controller

import io.exoquery.ActionReturningKind
import io.exoquery.Param
import io.exoquery.ParamBatchRefiner
import io.exoquery.ParamMulti
import io.exoquery.ParamSingle
import io.exoquery.SqlCompiledAction
import io.exoquery.SqlCompiledBatchAction
import io.exoquery.SqlCompiledQuery
import io.exoquery.xrError
import kotlinx.serialization.KSerializer
import io.exoquery.controller.StatementParam
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlin.collections.flatMap

// If it is a `param` instance it returns a single value, if it is a params instance it will return multiple
// This is safe because the token renderer will insert the needed `?` instances into the query
// and they will be in monitonic order. In the future for additional safety we might want to look into actually
// changing ParamMulti into a list of ParamSingle instances.
fun <T: Any> Param<T>.toStatementParam(): List<StatementParam<T>> =
  when (this) {
    is ParamBatchRefiner<*, *> ->
      xrError("Attempted to convert batch-param refiner to a database-statement parameter. This is illegal, all batch-param refiners need to be converted into normal Param instances first. The incorrect refiner was:\n${this.description}")
    is ParamMulti<*> ->
      this.value.flatMap { v -> listOf(StatementParam<T>(this.serial.serializer, this.serial.cls, v as T)) }
    is ParamSingle<*> ->
      listOf(StatementParam<T>(this.serial.serializer, this.serial.cls, value as T))
  }

fun <T> SqlCompiledQuery<T>.toControllerQuery(serializer: KSerializer<T>): ControllerQuery<T> =
  ControllerQuery(token.build(), params.flatMap { it.toStatementParam() }, serializer)


suspend fun <BatchInput, Input: Any, Output> SqlCompiledBatchAction<BatchInput, Input, Output>.toControllerBatchVerb(serializer: KSerializer<Output>): BatchVerb<Output> =
  when(actionReturningKind) {
    is ActionReturningKind.None -> {
      //Action(token.build(), params.map { it.toStatementParam() })
      // Check the kind of "Output" i.e. it needs to be a Long (we can use the descriptor-kind as a proxy for this and not need to pass a KClass in)
      if (serializer.descriptor.kind == PrimitiveKind.LONG)
        ControllerBatchAction(token.build(), produceBatchGroups().flatMap { g -> g.params.map { it.toStatementParam() } }) as BatchVerb<Output>
      else
        xrError("The action is not returning anything, but the serializer is not a Long. This is illegal. The serializer was:\n${serializer.descriptor}")
    }
    is ActionReturningKind.ClauseInQuery -> {
      // Try not passing the keys explicitly? If it's a action-returning do we need them?
      ControllerBatchActionReturning.Row(value, produceBatchGroups().flatMap { g -> g.params.map { it.toStatementParam() } }, serializer, listOf())
    }
    is ActionReturningKind.Keys -> {
      ControllerBatchActionReturning.Id(value, produceBatchGroups().flatMap { g -> g.params.map { it.toStatementParam() } }, serializer, (actionReturningKind as ActionReturningKind.Keys).columns)
    }
  }

suspend fun <Input, Output> SqlCompiledAction<Input, Output>.toControllerAction(serializer: KSerializer<Output>): ActionVerb<Output> =
  when(actionReturningKind) {
    is ActionReturningKind.None -> {
      // Check the kind of "Output" i.e. it needs to be a Long (we can use the descriptor-kind as a proxy for this and not need to pass a KClass in)
      if (serializer.descriptor.kind == PrimitiveKind.LONG)
        ControllerAction(token.build(), params.flatMap { it.toStatementParam() }) as ActionVerb<Output>
      else
        xrError("The action is not returning anything, but the serializer is not a Long. This is illegal. The serializer was:\n${serializer.descriptor}")
    }
    is ActionReturningKind.ClauseInQuery -> {
      // Try not passing the keys explicitly? If it's a action-returning do we need them?
      ControllerActionReturning.Row(value, params.flatMap { it.toStatementParam() }, serializer, listOf())
    }
    is ActionReturningKind.Keys -> {
      ControllerActionReturning.Id(value, params.flatMap { it.toStatementParam() }, serializer, (actionReturningKind as ActionReturningKind.Keys).columns)
    }
  }
