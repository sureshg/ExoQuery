package io.exoquery

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
    started.getPostgresDatabase().run(postgresScript)
    started
  }
  val postgres by lazy { embeddedPostgres.getPostgresDatabase() }

  val mysql: DataSource by lazy {
    HikariHelper.makeDataSource("testMysqlDB")
  }

  val sqlServer: DataSource by lazy {
    HikariHelper.makeDataSource("testSqlServerDB")
  }

  val h2: DataSource by lazy {
    HikariHelper.makeDataSource("testH2DB")
  }

  val sqlite: DataSource by lazy {
    HikariHelper.makeDataSource("testSqliteDB")
  }

  val oracle: DataSource by lazy {
    HikariHelper.makeDataSource("testOracleDB")
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
