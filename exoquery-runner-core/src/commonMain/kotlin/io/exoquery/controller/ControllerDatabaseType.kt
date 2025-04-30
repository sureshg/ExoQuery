package io.exoquery.controller

sealed interface ControllerDatabaseType {
  object Sqlite : ControllerDatabaseType
  object Postgres : ControllerDatabaseType
  object SqlServer : ControllerDatabaseType
  object MySql : ControllerDatabaseType
  object H2 : ControllerDatabaseType
  object Other : ControllerDatabaseType
}

fun ControllerDatabaseType.isSqlite(): Boolean = this is ControllerDatabaseType.Sqlite
fun ControllerDatabaseType.isPostgres(): Boolean = this is ControllerDatabaseType.Postgres
fun ControllerDatabaseType.isSqlServer(): Boolean = this is ControllerDatabaseType.SqlServer
fun ControllerDatabaseType.isH2(): Boolean = this is ControllerDatabaseType.H2
fun ControllerDatabaseType.isMysql(): Boolean = this is ControllerDatabaseType.MySql
