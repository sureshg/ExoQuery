package io.exoquery.native

import io.exoquery.Param
import io.exoquery.ParamBatchRefiner
import io.exoquery.ParamMulti
import io.exoquery.ParamSingle
import io.exoquery.SqlCompiledQuery
import io.exoquery.xrError
import io.exoquery.controller.ExecutionOptions
import io.exoquery.controller.Query
import io.exoquery.controller.StatementParam
import io.exoquery.controller.native.DatabaseController
import io.exoquery.controller.runOn
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

// Change testing-controller to controller-common and it in there?
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

suspend fun <T> SqlCompiledQuery<T>.runOn(database: DatabaseController, serializer: KSerializer<T>, options: ExecutionOptions = ExecutionOptions()) =
  this.toControllerQuery(serializer).runOn(database, options)

inline suspend fun <reified T: Any> SqlCompiledQuery<T>.runOn(database: DatabaseController, options: ExecutionOptions = ExecutionOptions()) =
  this.runOn(database, serializer(), options)
