package io.exoquery.controller

actual fun identityDatabaseType(controller: Controller<*>): ControllerDatabaseType =
  // Currently only sqlite supported on native
  ControllerDatabaseType.Sqlite
