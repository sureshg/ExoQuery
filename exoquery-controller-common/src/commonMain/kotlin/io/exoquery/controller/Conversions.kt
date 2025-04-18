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

fun <T: Any> Param<T>.toStatementParam(): StatementParam<T> =
  when (this) {
    is ParamBatchRefiner<*, *> ->
      xrError("Attempted to convert batch-param refiner to a database-statement parameter. This is illegal, all batch-param refiners need to be converted into normal Param instances first. The incorrect refiner was:\n${this.description}")
    is ParamMulti<*> ->
      xrError("Attempted to convert multi-param to a database-statement parameter. This is illegal, all multi-params need to be converted into normal Param instances first. The incorrect param was:\n${this.description}")
    is ParamSingle<*> ->
      StatementParam<T>(this.serial.serializer, this.serial.cls, value as T)
  }

fun <T> SqlCompiledQuery<T>.toControllerQuery(serializer: KSerializer<T>): ControllerQuery<T> =
  ControllerQuery(token.build(), params.map { it.toStatementParam() }, serializer)


suspend fun <BatchInput, Input: Any, Output> SqlCompiledBatchAction<BatchInput, Input, Output>.toControllerBatchVerb(serializer: KSerializer<Output>): BatchVerb<Output> =
  when(actionReturningKind) {
    is ActionReturningKind.None -> {
      //Action(token.build(), params.map { it.toStatementParam() })
      // Check the kind of "Output" i.e. it needs to be a Long (we can use the descriptor-kind as a proxy for this and not need to pass a KClass in)
      if (serializer.descriptor.kind == PrimitiveKind.LONG)
        ControllerBatchAction(token.build(), produceBatchGroups().map { g -> g.params.map { it.toStatementParam() } }) as BatchVerb<Output>
      else
        xrError("The action is not returning anything, but the serializer is not a Long. This is illegal. The serializer was:\n${serializer.descriptor}")
    }
    is ActionReturningKind.ClauseInQuery -> {
      // Try not passing the keys explicitly? If it's a action-returning do we need them?
      ControllerBatchActionReturning.Row(value, produceBatchGroups().map { g -> g.params.map { it.toStatementParam() } }, serializer, listOf())
    }
    is ActionReturningKind.Keys -> {
      ControllerBatchActionReturning.Id(value, produceBatchGroups().map { g -> g.params.map { it.toStatementParam() } }, serializer, (actionReturningKind as ActionReturningKind.Keys).columns)
    }
  }

suspend fun <Input, Output> SqlCompiledAction<Input, Output>.toControllerAction(serializer: KSerializer<Output>): ActionVerb<Output> =
  when(actionReturningKind) {
    is ActionReturningKind.None -> {
      // Check the kind of "Output" i.e. it needs to be a Long (we can use the descriptor-kind as a proxy for this and not need to pass a KClass in)
      if (serializer.descriptor.kind == PrimitiveKind.LONG)
        ControllerAction(token.build(), params.map { it.toStatementParam() }) as ActionVerb<Output>
      else
        xrError("The action is not returning anything, but the serializer is not a Long. This is illegal. The serializer was:\n${serializer.descriptor}")
    }
    is ActionReturningKind.ClauseInQuery -> {
      // Try not passing the keys explicitly? If it's a action-returning do we need them?
      ControllerActionReturning.Row(value, params.map { it.toStatementParam() }, serializer, listOf())
    }
    is ActionReturningKind.Keys -> {
      ControllerActionReturning.Id(value, params.map { it.toStatementParam() }, serializer, (actionReturningKind as ActionReturningKind.Keys).columns)
    }
  }
