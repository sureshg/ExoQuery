package io.exoquery.jdbc

import io.exoquery.SqlCompiledBatchAction
import io.exoquery.controller.BatchAction
import io.exoquery.controller.BatchActionReturningId
import io.exoquery.controller.BatchActionReturningRow
import io.exoquery.controller.jdbc.JdbcExecutionOptions
import io.exoquery.controller.jdbc.JdbcControllers
import io.exoquery.controller.jdbc.JdbcController
import io.exoquery.controller.runOn
import io.exoquery.controller.toControllerBatchVerb
import io.exoquery.printing.MessagesRuntime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import javax.sql.DataSource

suspend fun <BatchInput, Input: Any, Output> SqlCompiledBatchAction<BatchInput, Input, Output>.runOn(database: JdbcController, serializer: KSerializer<Output>, options: JdbcExecutionOptions = JdbcExecutionOptions()): List<Output> =
  when (val action = this.toControllerBatchVerb(serializer)) {
    is BatchAction -> action.runOn(database, options) as List<Output>
    is BatchActionReturningId<Output> -> {
      when {
        actionKind.isUpdateOrDelete() && database.isSqlite() ->
          throw IllegalStateException("SQLite does not support returning ids with returningKeys in UPDATE and DELETE queries. Use .returning instead to add a RETRUNING clause to the query.\n${MessagesRuntime.ReturningExplanation}")
        actionKind.isUpdateOrDelete() && database.isSqlServer() ->
          throw IllegalStateException("SQL Server does not support returning ids with returningKeys in UPDATE and DELETE queries. Use .returning instead to add a OUTPUT clause to the query.\n${MessagesRuntime.ReturningExplanation}")
        actionKind.isDelete() && database.isH2() ->
          throw IllegalStateException("H2 only supports the `returningKeys` construct with INSERT and UPDATE queries (and H2 does not support `retruning` at all).\n${MessagesRuntime.ReturningExplanation}")
        database.isSqlite() ->
          throw IllegalStateException(
            "SQLite has extremely strange behaviors with PrepareStatement.getGeneratedKeys and Batch Queries (that `query.returningKeys` relies on).\n" +
            "For SQLite use `query.returning` to create a RETURNING clause instead.\n${MessagesRuntime.ReturningExplanation}")
        else ->
          Unit
      }
      action.runOn(database, options)
    }
    is BatchActionReturningRow<Output> -> {
      when {
        database.isH2() ->
          throw IllegalStateException("H2 Server does not support the action.returning(...) API. Only `returningKeys` can be used with H2 and only in INSERT and UPDATE queries.\n${MessagesRuntime.ReturningExplanation}")
      }
      action.runOn(database, options)
    }
  }

inline suspend fun <BatchInput, Input: Any, reified Output> SqlCompiledBatchAction<BatchInput, Input, Output>.runOn(database: JdbcController, options: JdbcExecutionOptions = JdbcExecutionOptions()) =
  this.runOn(database, serializer<Output>(), options)

inline suspend fun <BatchInput, reified Input: Any, reified Output> SqlCompiledBatchAction<BatchInput, Input, Output>.runOnPostgres(dataSource: DataSource) = run {
  val controller = JdbcControllers.Postgres(dataSource)
  this.runOn(controller, serializer<Output>())
}
