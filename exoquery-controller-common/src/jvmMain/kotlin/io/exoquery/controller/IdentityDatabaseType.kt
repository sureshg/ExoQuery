package io.exoquery.controller

import io.exoquery.controller.jdbc.JdbcControllers

actual fun identityDatabaseType(controller: Controller<*>): ControllerDatabaseType {
  return when (controller) {
    is JdbcControllers.Sqlite -> ControllerDatabaseType.Sqlite
    is JdbcControllers.Postgres -> ControllerDatabaseType.Postgres
    is JdbcControllers.SqlServer -> ControllerDatabaseType.SqlServer
    is JdbcControllers.Mysql -> ControllerDatabaseType.MySql
    is JdbcControllers.H2 -> ControllerDatabaseType.H2
    else -> ControllerDatabaseType.Other
  }
}
