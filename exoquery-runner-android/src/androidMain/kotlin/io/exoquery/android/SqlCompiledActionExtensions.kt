package io.exoquery.android

import io.exoquery.SqlCompiledAction
import io.exoquery.controller.android.AndroidDatabaseController
import kotlinx.serialization.KSerializer
import io.exoquery.runOn as runOnCommon

suspend fun <Input, Output> SqlCompiledAction<Input, Output>.runOn(database: AndroidDatabaseController, serializer: KSerializer<Output>): Output =
  runOnCommon(database, serializer)

inline suspend fun <Input, reified Output> SqlCompiledAction<Input, Output>.runOn(database: AndroidDatabaseController) =
  runOnCommon(database)
