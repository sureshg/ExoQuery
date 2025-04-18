package io.exoquery.android

import androidx.sqlite.db.SupportSQLiteDatabase
import io.exoquery.controller.runOn

import io.exoquery.SqlCompiledQuery
import io.exoquery.controller.android.AndroidDatabaseController
import io.exoquery.controller.android.UnusedOpts
import io.exoquery.controller.runOn
import io.exoquery.controller.toControllerQuery
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import javax.sql.DataSource

private interface ActionOutput<T> {
    class Returning<T>(val serializer: KSerializer<*>) : ActionOutput<T>
    object NoReturning : ActionOutput<Long>
}

suspend fun <T> SqlCompiledQuery<T>.runOn(database: AndroidDatabaseController, serializer: KSerializer<T>) =
    this.toControllerQuery(serializer).runOn(database)

inline suspend fun <reified T: Any> SqlCompiledQuery<T>.runOn(database: AndroidDatabaseController) =
    this.runOn(database, serializer<T>())

inline suspend fun <reified T: Any> SqlCompiledQuery<T>.runOnSession(db: SupportSQLiteDatabase) = run {
    val controller = AndroidDatabaseController.fromSingleSession(db)
    this.runOn(controller, serializer())
}
