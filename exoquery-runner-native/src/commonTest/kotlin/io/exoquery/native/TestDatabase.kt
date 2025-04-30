package io.exoquery.native

import co.touchlab.sqliter.DatabaseFileContext.deleteDatabase
import io.exoquery.BasicSchemaTerpal
import io.exoquery.SqliteSchemaString
import io.exoquery.controller.native.NativeDatabaseController
import kotlinx.coroutines.runBlocking

object TestDatabase {
  val name = "exoquery_test.db"

  //val basePath = "/home/alexi/git/terpal-sql/terpal-sql-native/"
  val basePath = "./"
  val ctx by lazy {
    runBlocking {
      deleteDatabase(name, basePath)
      NativeDatabaseController.fromSchema(BasicSchemaTerpal, name, basePath)
    }
  }

  fun run(query: String) {
    ctx.runRaw(SqliteSchemaString)
  }

  //fun emptyDatabaseConfig() = run {
  //  DatabaseConfiguration(
  //    name = name,
  //    version = 1,
  //    create = { connection -> wrapConnection(connection) { Unit } },
  //    upgrade = { connection, oldVersion, newVersion -> Unit }
  //  )
  //}
}
