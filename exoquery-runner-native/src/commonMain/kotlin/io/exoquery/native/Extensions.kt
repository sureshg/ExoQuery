package io.exoquery.native

import io.exoquery.SqlCompiledAction
import io.exoquery.SqlCompiledQuery
import io.exoquery.controller.native.NativeDatabaseController
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import io.exoquery.runOn as runOnCommon

inline suspend fun <reified T : Any> SqlCompiledQuery<T>.runOn(database: NativeDatabaseController) =
  runOnCommon(database, serializer())

suspend fun <Input, Output> SqlCompiledAction<Input, Output>.runOn(database: NativeDatabaseController, serializer: KSerializer<Output>): Output =
  runOnCommon(database, serializer)

inline suspend fun <Input, reified Output> SqlCompiledAction<Input, Output>.runOn(database: NativeDatabaseController) =
  this.runOn(database, serializer<Output>())
