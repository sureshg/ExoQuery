package io.exoquery.testdata

import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import io.exoquery.testdata.SqliteSchemaString
import io.exoquery.controller.Controller
import io.exoquery.controller.runActions
import io.exoquery.controller.sqlite.CallAfterVersion
import io.exoquery.controller.sqlite.TerpalSchema

object EmptySchema : SqlSchema<QueryResult.Value<Unit>> {
  override val version: Long = 1
  override fun create(driver: SqlDriver): QueryResult.Value<Unit> = QueryResult.Companion.Unit
  override fun migrate(
    driver: SqlDriver,
    oldVersion: Long,
    newVersion: Long,
    vararg callbacks: AfterVersion,
  ) = QueryResult.Companion.Unit
}

object BasicSchemaTerpal : TerpalSchema<Unit> {
  override val version: Long = 1
  override suspend fun create(driver: Controller<*>): Unit {
    driver.runActions(SqliteSchemaString)
  }

  override suspend fun migrate(
    driver: Controller<*>,
    oldVersion: Long,
    newVersion: Long,
    vararg callbacks: CallAfterVersion
  ) {
  }
}
