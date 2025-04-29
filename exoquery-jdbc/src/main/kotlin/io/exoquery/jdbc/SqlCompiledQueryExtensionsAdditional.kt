package io.exoquery.jdbc

import io.exoquery.SqlCompiledQuery
import io.exoquery.controller.jdbc.JdbcController
import io.exoquery.controller.jdbc.JdbcControllers
import io.exoquery.controller.jdbc.JdbcExecutionOptions
import io.exoquery.controller.runOn
import io.exoquery.controller.toControllerQuery
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import javax.sql.DataSource
import io.exoquery.runOn as runOnCommon

// If JdbcOptions don't need to be set, use the common run implementation
inline suspend fun <reified T> SqlCompiledQuery<T>.runOn(database: JdbcController) =
  runOnCommon(database, serializer<T>())

suspend fun <T> SqlCompiledQuery<T>.runOn(database: JdbcController, serializer: KSerializer<T>) =
  runOnCommon(database, serializer)

inline suspend fun <reified T : Any> SqlCompiledQuery<T>.runOn(database: JdbcController, options: JdbcExecutionOptions) =
  this.runOn(database, serializer<T>(), options)

suspend fun <T> SqlCompiledQuery<T>.runOn(database: JdbcController, serializer: KSerializer<T>, options: JdbcExecutionOptions) =
  this.toControllerQuery(serializer).runOn(database, options)

inline suspend fun <reified T : Any> SqlCompiledQuery<T>.runOnPostgres(dataSource: DataSource) = run {
  val controller = JdbcControllers.Postgres(dataSource)
  this.runOn(controller, serializer<T>())
}
