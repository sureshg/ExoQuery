package io.exoquery.android

import io.exoquery.ActionKind
import io.exoquery.ActionReturningKind
import io.exoquery.SqlCompiledAction
import io.exoquery.controller.ControllerAction
import io.exoquery.controller.ControllerActionReturning
import io.exoquery.controller.android.AndroidDatabaseController
import io.exoquery.controller.runOn
import io.exoquery.controller.toControllerAction
import io.exoquery.controller.toStatementParam
import io.exoquery.illegalOp
import io.exoquery.xrError
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.serializer

suspend fun <Input, Output> SqlCompiledAction<Input, Output>.runOn(database: AndroidDatabaseController, serializer: KSerializer<Output>): Output =
  when (val action = this.toControllerAction(serializer)) {
    is ControllerAction ->
      action.runOn(database) as Output
    is ControllerActionReturning.Id<Output> ->
      action.runOn(database)
    is ControllerActionReturning.Row<Output> ->
      action.runOn(database)
  }

inline suspend fun <Input, reified Output> SqlCompiledAction<Input, Output>.runOn(database: AndroidDatabaseController) =
  this.runOn(database, serializer<Output>())
