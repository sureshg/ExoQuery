package io.exoquery

import io.exoquery.annotation.ExoBuildDatabaseSpecific
import io.exoquery.sql.MySqlDialect
import io.exoquery.sql.PostgresDialect
import io.exoquery.sql.SqlServerDialect

interface BuildFor<T> {
  @ExoBuildDatabaseSpecific(PostgresDialect::class)
  fun Postgres(): T

  @ExoBuildDatabaseSpecific(PostgresDialect::class)
  fun Postgres(label: String): T

  @ExoBuildDatabaseSpecific(SqliteDialect::class)
  fun Sqlite(): T

  @ExoBuildDatabaseSpecific(SqliteDialect::class)
  fun Sqlite(label: String): T

  @ExoBuildDatabaseSpecific(MySqlDialect::class)
  fun MySql(): T

  @ExoBuildDatabaseSpecific(MySqlDialect::class)
  fun MySql(label: String): T

  @ExoBuildDatabaseSpecific(SqlServerDialect::class)
  fun SqlServer(): T

  @ExoBuildDatabaseSpecific(SqlServerDialect::class)
  fun SqlServer(label: String): T

  @ExoBuildDatabaseSpecific(H2Dialect::class)
  fun H2(): T

  @ExoBuildDatabaseSpecific(H2Dialect::class)
  fun H2(label: String): T
}
