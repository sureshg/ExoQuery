package io.exoquery

import io.exoquery.SqlCompiledQuery
import io.exoquery.Param
import io.exoquery.controller.Action
import io.exoquery.controller.ActionReturningId
import io.exoquery.controller.ActionReturningRow
import io.exoquery.controller.BatchAction
import io.exoquery.controller.BatchActionReturningId
import io.exoquery.controller.BatchActionReturningRow
import io.exoquery.controller.BatchVerb
import io.exoquery.controller.jdbc.JdbcExecutionOptions
import io.exoquery.controller.Query
import io.exoquery.controller.StatementParam
import io.exoquery.controller.jdbc.JdbcControllers
import io.exoquery.controller.jdbc.JdbcController
import io.exoquery.controller.runOn
import io.exoquery.controller.toControllerBatchVerb
import io.exoquery.controller.toStatementParam
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.serializer
import javax.sql.DataSource

suspend fun <BatchInput, Input: Any, Output> SqlCompiledBatchAction<BatchInput, Input, Output>.runOn(database: JdbcController, serializer: KSerializer<Output>, options: JdbcExecutionOptions = JdbcExecutionOptions()): List<Output> =
  when (val action = this.toControllerBatchVerb(serializer)) {
    is BatchAction -> action.runOn(database, options) as List<Output>
    is BatchActionReturningId<Output> -> {
      if (actionKind.isUpdateOrDelete() && database is JdbcControllers.Sqlite)
        throw IllegalStateException("SQLite does not support returning ids with returningKeys. Use .retruning instead to add a RETRUNING clause to the query.")
      action.runOn(database, options)
    }
    is BatchActionReturningRow<Output> -> action.runOn(database, options)
  }

inline suspend fun <BatchInput, Input: Any, reified Output> SqlCompiledBatchAction<BatchInput, Input, Output>.runOn(database: JdbcController, options: JdbcExecutionOptions = JdbcExecutionOptions()) =
  this.runOn(database, serializer<Output>(), options)

inline suspend fun <BatchInput, reified Input: Any, reified Output> SqlCompiledBatchAction<BatchInput, Input, Output>.runOnPostgres(dataSource: DataSource) = run {
  val controller = JdbcControllers.Postgres(dataSource)
  this.runOn(controller, serializer<Output>())
}
