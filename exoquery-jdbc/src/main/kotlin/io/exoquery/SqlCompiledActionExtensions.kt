package io.exoquery

import io.exoquery.SqlCompiledQuery
import io.exoquery.Param
import io.exoquery.controller.Action
import io.exoquery.controller.ActionReturningId
import io.exoquery.controller.ActionReturningRow
import io.exoquery.controller.ActionVerb
import io.exoquery.controller.BatchAction
import io.exoquery.controller.BatchActionReturningId
import io.exoquery.controller.BatchActionReturningRow
import io.exoquery.controller.jdbc.JdbcExecutionOptions
import io.exoquery.controller.Query
import io.exoquery.controller.StatementParam
import io.exoquery.controller.jdbc.JdbcControllers
import io.exoquery.controller.jdbc.JdbcController
import io.exoquery.controller.runOn
import io.exoquery.controller.toControllerAction
import io.exoquery.controller.toStatementParam
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.serializer
import javax.sql.DataSource

suspend fun <Input, Output> SqlCompiledAction<Input, Output>.runOn(database: JdbcController, serializer: KSerializer<Output>, options: JdbcExecutionOptions = JdbcExecutionOptions()): Output =
  when (val action = this.toControllerAction(serializer)) {
    is Action -> action.runOn(database, options) as Output
    is ActionReturningId<Output> -> action.runOn(database, options)
    is ActionReturningRow<Output> -> action.runOn(database, options)
  }

inline suspend fun <Input, reified Output> SqlCompiledAction<Input, Output>.runOn(database: JdbcController, options: JdbcExecutionOptions = JdbcExecutionOptions()) =
  this.runOn(database, serializer<Output>(), options)

inline suspend fun <reified Input, reified Output> SqlCompiledAction<Input, Output>.runOnPostgres(dataSource: DataSource) = run {
  val controller = JdbcControllers.Postgres(dataSource)
  this.runOn(controller, serializer<Output>())
}
