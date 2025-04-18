package io.exoquery.jdbc

import io.exoquery.SqlCompiledAction
import io.exoquery.controller.ControllerAction
import io.exoquery.controller.ControllerActionReturning
import io.exoquery.controller.jdbc.JdbcExecutionOptions
import io.exoquery.controller.jdbc.JdbcControllers
import io.exoquery.controller.jdbc.JdbcController
import io.exoquery.controller.runOn
import io.exoquery.controller.toControllerAction
import io.exoquery.printing.MessagesRuntime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import javax.sql.DataSource

fun JdbcController.isSqlite(): Boolean = this is JdbcControllers.Sqlite
fun JdbcController.isSqlServer(): Boolean = this is JdbcControllers.SqlServer
fun JdbcController.isH2(): Boolean = this is JdbcControllers.H2
fun JdbcController.isMysql(): Boolean = this is JdbcControllers.Mysql



suspend fun <Input, Output> SqlCompiledAction<Input, Output>.runOn(database: JdbcController, serializer: KSerializer<Output>, options: JdbcExecutionOptions = JdbcExecutionOptions()): Output =
  when (val action = this.toControllerAction(serializer)) {
    is ControllerAction -> action.runOn(database, options) as Output
    is ControllerActionReturning.Id<Output> -> {
      when {
        actionKind.isUpdateOrDelete() && database.isSqlite() ->
          throw IllegalStateException("SQLite does not support returning ids with returningKeys in UPDATE and DELETE queries. Use .returning instead to add a RETRUNING clause to the query.\n${MessagesRuntime.ReturningExplanation}")
        actionKind.isUpdateOrDelete() && database.isSqlServer() ->
          throw IllegalStateException("SQL Server does not support returning ids with returningKeys in UPDATE and DELETE queries. Use .returning instead to add a OUTPUT clause to the query.\n${MessagesRuntime.ReturningExplanation}")
        actionKind.isDelete() && database.isH2() ->
          throw IllegalStateException("H2 only supports the `returningKeys` construct with INSERT and UPDATE queries (and H2 does not support `retruning` at all).\n${MessagesRuntime.ReturningExplanation}")
        else ->
          Unit
      }
      action.runOn(database, options)
    }
    is ControllerActionReturning.Row<Output> -> {
      when {
        database.isH2() ->
          throw IllegalStateException("H2 Server does not support the action.returning(...) API. Only `returningKeys` can be used with H2 and only in INSERT and UPDATE queries.\n${MessagesRuntime.ReturningExplanation}")
      }
      action.runOn(database, options)
    }
  }

inline suspend fun <Input, reified Output> SqlCompiledAction<Input, Output>.runOn(database: JdbcController, options: JdbcExecutionOptions = JdbcExecutionOptions()) =
  this.runOn(database, serializer<Output>(), options)

inline suspend fun <reified Input, reified Output> SqlCompiledAction<Input, Output>.runOnPostgres(dataSource: DataSource) = run {
  val controller = JdbcControllers.Postgres(dataSource)
  this.runOn(controller, serializer<Output>())
}
