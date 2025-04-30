package io.exoquery.android

import androidx.sqlite.db.SupportSQLiteDatabase
import io.exoquery.SqlCompiledQuery
import io.exoquery.controller.android.AndroidDatabaseController
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import io.exoquery.runOn as runOnCommon

suspend fun <T> SqlCompiledQuery<T>.runOn(database: AndroidDatabaseController, serializer: KSerializer<T>) =
  runOnCommon(database, serializer)

inline suspend fun <reified T : Any> SqlCompiledQuery<T>.runOn(database: AndroidDatabaseController) =
  runOnCommon(database, serializer<T>())

inline suspend fun <reified T : Any> SqlCompiledQuery<T>.runOnSession(db: SupportSQLiteDatabase) = run {
  val controller = AndroidDatabaseController.fromSingleSession(db)
  this.runOn(controller, serializer())
}
