package io.exoquery.testdata

import io.exoquery.testdata.SqliteSchemaString
import io.exoquery.controller.Controller
import io.exoquery.controller.runActions
import io.exoquery.controller.sqlite.CallAfterVersion
import io.exoquery.controller.sqlite.TerpalSchema

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
