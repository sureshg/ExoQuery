package io.exoquery.postgres

import io.exoquery.Person
import io.exoquery.PostgresDialect
import io.exoquery.capture
import io.exoquery.controller.Controller
import io.exoquery.controller.jdbc.JdbcController
import io.exoquery.runOn
import io.exoquery.sql.SqlIdiom

suspend fun JdbcController.people() = capture { Table<Person>() }.build<PostgresDialect>().runOn(this)
