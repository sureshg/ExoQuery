package io.exoquery

import io.exoquery.controller.jdbc.DatabaseController
import io.exoquery.controller.jdbc.HikariHelper
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import javax.sql.DataSource
import kotlin.io.readText
import kotlin.jvm.java

object TestDatabases {
  val embeddedPostgres by lazy {
    val started = EmbeddedPostgres.start()
    val postgresScriptsPath = "/db/postgres-schema.sql"
    val resource = this::class.java.getResource(postgresScriptsPath)
    if (resource == null) throw NullPointerException("The postgres script path `$postgresScriptsPath` was not found")
    val postgresScript = resource.readText()
    println("---------- Postgres Running on: ${started.getJdbcUrl("postgres", "")}")
    started.getPostgresDatabase().run(postgresScript)
    started
  }
  val postgres by lazy { DatabaseController.Postgres(embeddedPostgres.getPostgresDatabase()) }

  val mysql by lazy {
    DatabaseController.Mysql(HikariHelper.makeDataSource("testMysqlDB"))
  }

  val sqlServer by lazy {
    DatabaseController.SqlServer(HikariHelper.makeDataSource("testSqlServerDB"))
  }

  val h2 by lazy {
    DatabaseController.H2(HikariHelper.makeDataSource("testH2DB"))
  }

  val sqlite by lazy {
    DatabaseController.Sqlite(HikariHelper.makeDataSource("testSqliteDB"))
  }

  val oracle by lazy {
    DatabaseController.Oracle(HikariHelper.makeDataSource("testOracleDB"))
  }
}

fun DataSource.run(sql: String) =
  this.getConnection().use { conn ->
    sql.split(";").map { it.trim() }.filter { !it.isEmpty() }.forEach { sqlSplit ->
      conn.createStatement().use { stmt ->
        stmt.execute(sqlSplit)
      }
    }
  }
