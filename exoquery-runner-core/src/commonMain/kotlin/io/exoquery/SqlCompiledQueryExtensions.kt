package io.exoquery

import io.exoquery.controller.Controller
import io.exoquery.controller.runOn
import io.exoquery.controller.toControllerQuery
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

suspend fun <T> SqlCompiledQuery<T>.runOn(database: Controller<*>, serializer: KSerializer<T>) =
  this.toControllerQuery(serializer).runOn(database)

inline suspend fun <reified T : Any> SqlCompiledQuery<T>.runOn(database: Controller<*>) =
  this.runOn(database, serializer<T>())
