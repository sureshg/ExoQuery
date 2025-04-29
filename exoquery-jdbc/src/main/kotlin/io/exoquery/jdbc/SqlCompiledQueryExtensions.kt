package io.exoquery.jdbc

import io.exoquery.SqlCompiledQuery
import io.exoquery.controller.jdbc.JdbcExecutionOptions
import io.exoquery.controller.jdbc.JdbcControllers
import io.exoquery.controller.jdbc.JdbcController
import io.exoquery.controller.runOn
import io.exoquery.controller.toControllerQuery
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import javax.sql.DataSource

private interface ActionOutput<T> {
  class Returning<T>(val serializer: KSerializer<*>) : ActionOutput<T>
  object NoReturning : ActionOutput<Long>
}

suspend fun <T> SqlCompiledQuery<T>.runOn(database: JdbcController, serializer: KSerializer<T>, options: JdbcExecutionOptions = JdbcExecutionOptions()) =
  this.toControllerQuery(serializer).runOn(database, options)

inline suspend fun <reified T : Any> SqlCompiledQuery<T>.runOn(database: JdbcController, options: JdbcExecutionOptions = JdbcExecutionOptions()) =
  this.runOn(database, serializer(), options)

inline suspend fun <reified T : Any> SqlCompiledQuery<T>.runOnPostgres(dataSource: DataSource) = run {
  val controller = JdbcControllers.Postgres(dataSource)
  this.runOn(controller, serializer())
}
