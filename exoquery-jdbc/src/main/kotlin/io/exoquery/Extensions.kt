package io.exoquery

import io.exoquery.SqlCompiledQuery
import io.exoquery.Param
import io.exoquery.controller.Action
import io.exoquery.controller.ActionReturningId
import io.exoquery.controller.ActionReturningRow
import io.exoquery.controller.BatchAction
import io.exoquery.controller.BatchActionReturningId
import io.exoquery.controller.BatchActionReturningRow
import io.exoquery.controller.jdbc.JdbcExecutionOptions
import io.exoquery.controller.Query
import io.exoquery.controller.StatementParam
import io.exoquery.controller.jdbc.JdbcControllers
import io.exoquery.controller.jdbc.JdbcController
import io.exoquery.controller.runOn
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.serializer
import javax.sql.DataSource

internal fun <T: Any> Param<T>.toStatementParam(): StatementParam<T> =
  when (this) {
    is ParamBatchRefiner<*, *> ->
      xrError("Attempted to convert batch-param refiner to a database-statement parameter. This is illegal, all batch-param refiners need to be converted into normal Param instances first. The incorrect refiner was:\n${this.description}")
    is ParamMulti<*> ->
      xrError("Attempted to convert multi-param to a database-statement parameter. This is illegal, all multi-params need to be converted into normal Param instances first. The incorrect param was:\n${this.description}")
    is ParamSingle<*> ->
      StatementParam<T>(this.serial.serializer, this.serial.cls, value as T)
}

internal fun <T> SqlCompiledQuery<T>.toControllerQuery(serializer: KSerializer<T>): Query<T> =
  Query(token.build(), params.map { it.toStatementParam() }, serializer)

private interface ActionOutput<T> {
  class Returning<T>(val serializer: KSerializer<*>) : ActionOutput<T>
  object NoReturning : ActionOutput<Long>
}

suspend fun <T> SqlCompiledQuery<T>.runOn(database: JdbcController, serializer: KSerializer<T>, options: JdbcExecutionOptions = JdbcExecutionOptions()) =
  this.toControllerQuery(serializer).runOn(database, options)

inline suspend fun <reified T: Any> SqlCompiledQuery<T>.runOn(database: JdbcController, options: JdbcExecutionOptions = JdbcExecutionOptions()) =
  this.runOn(database, serializer(), options)

inline suspend fun <reified T: Any> SqlCompiledQuery<T>.runOnPostgres(dataSource: DataSource) = run {
  val controller = JdbcControllers.Postgres(dataSource)
  this.runOn(controller, serializer())
}

suspend fun <Input, Output> SqlCompiledAction<Input, Output>.runOn(database: JdbcController, serializer: KSerializer<Output>, options: JdbcExecutionOptions = JdbcExecutionOptions()): Output =
  when(actionReturningKind) {
    is ActionReturningKind.None -> {
      val action = Action(token.build(), params.map { it.toStatementParam() })
      // Check the kind of "Output" i.e. it needs to be a Long (we can use the descriptor-kind as a proxy for this and not need to pass a KClass in)
      if (serializer.descriptor.kind == PrimitiveKind.LONG)
        action.runOn(database, options) as Output
      else
        xrError("The action is not returning anything, but the serializer is not a Long. This is illegal. The serializer was:\n${serializer.descriptor}")
    }
    is ActionReturningKind.ClauseInQuery -> {
      // Try not passing the keys explicitly? If it's a action-returning do we need them?
      val actionReturning = ActionReturningRow(value, params.map { it.toStatementParam() }, serializer, listOf())
      actionReturning.runOn(database, options)
    }
     is ActionReturningKind.Keys -> {
      val actionReturningId = ActionReturningId(value, params.map { it.toStatementParam() }, serializer, (actionReturningKind as ActionReturningKind.Keys).columns)
      actionReturningId.runOn(database, options)
    }
  }


inline suspend fun <Input, reified Output> SqlCompiledAction<Input, Output>.runOn(database: JdbcController, options: JdbcExecutionOptions = JdbcExecutionOptions()) =
  this.runOn(database, serializer<Output>(), options)

inline suspend fun <reified Input, reified Output> SqlCompiledAction<Input, Output>.runOnPostgres(dataSource: DataSource) = run {
  val controller = JdbcControllers.Postgres(dataSource)
  this.runOn(controller, serializer<Output>())
}

suspend fun <BatchInput, Input: Any, Output> SqlCompiledBatchAction<BatchInput, Input, Output>.runOn(database: JdbcController, serializer: KSerializer<Output>, options: JdbcExecutionOptions = JdbcExecutionOptions()): List<Output> =
  when(actionReturningKind) {
    is ActionReturningKind.None -> {
      val action = BatchAction(token.build(), produceBatchGroups().map { g -> g.params.map { it.toStatementParam() } })
        //Action(token.build(), params.map { it.toStatementParam() })
      // Check the kind of "Output" i.e. it needs to be a Long (we can use the descriptor-kind as a proxy for this and not need to pass a KClass in)
      if (serializer.descriptor.kind == PrimitiveKind.LONG)
        action.runOn(database, options) as List<Output>
      else
        xrError("The action is not returning anything, but the serializer is not a Long. This is illegal. The serializer was:\n${serializer.descriptor}")
    }
    is ActionReturningKind.ClauseInQuery -> {
      // Try not passing the keys explicitly? If it's a action-returning do we need them?
      val actionReturning = BatchActionReturningRow(value, produceBatchGroups().map { g -> g.params.map { it.toStatementParam() } }, serializer, listOf())
      actionReturning.runOn(database, options)
    }
     is ActionReturningKind.Keys -> {
      val actionReturningId = BatchActionReturningId(value, produceBatchGroups().map { g -> g.params.map { it.toStatementParam() } }, serializer, (actionReturningKind as ActionReturningKind.Keys).columns)
      actionReturningId.runOn(database, options)
    }
  }

inline suspend fun <BatchInput, Input: Any, reified Output> SqlCompiledBatchAction<BatchInput, Input, Output>.runOn(database: JdbcController, options: JdbcExecutionOptions = JdbcExecutionOptions()) =
  this.runOn(database, serializer<Output>(), options)

inline suspend fun <BatchInput, reified Input: Any, reified Output> SqlCompiledBatchAction<BatchInput, Input, Output>.runOnPostgres(dataSource: DataSource) = run {
  val controller = JdbcControllers.Postgres(dataSource)
  this.runOn(controller, serializer<Output>())
}
