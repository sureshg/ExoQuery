package io.exoquery.android

import io.exoquery.ActionKind
import io.exoquery.ActionReturningKind
import io.exoquery.SqlCompiledAction
import io.exoquery.controller.Action
import io.exoquery.controller.ActionReturningId
import io.exoquery.controller.ActionReturningRow
import io.exoquery.controller.android.AndroidDatabaseController
import io.exoquery.controller.runOn
import io.exoquery.controller.toStatementParam
import io.exoquery.illegalOp
import io.exoquery.xrError
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind

suspend fun <Input, Output> SqlCompiledAction<Input, Output>.runOn(database: AndroidDatabaseController, serializer: KSerializer<Output>): Output =
  when(actionReturningKind) {
    is ActionReturningKind.None -> {
      val action = Action(token.build(), params.map { it.toStatementParam() })
      // Check the kind of "Output" i.e. it needs to be a Long (we can use the descriptor-kind as a proxy for this and not need to pass a KClass in)
      if (serializer.descriptor.kind == PrimitiveKind.LONG)
        action.runOn(database) as Output
      else
        illegalOp("The action is not returning anything, but the serializer is not a Long. This is illegal. The serializer was:\n${serializer.descriptor}")
    }
    is ActionReturningKind.ClauseInQuery -> {
      // Try not passing the keys explicitly? If it's a action-returning do we need them?
      val actionReturning = ActionReturningRow(value, params.map { it.toStatementParam() }, serializer, listOf())
      actionReturning.runOn(database)
    }
     is ActionReturningKind.Keys -> {
      val actionReturningId = ActionReturningId(value, params.map { it.toStatementParam() }, Long.serializer(), (actionReturningKind as ActionReturningKind.Keys).columns)
      val queryOutput = actionReturningId.runOn(database)

       if (actionKind != ActionKind.Insert)
         illegalOp(MessagesAndroid.SqliteNativeCantReturningKeysIfNotInsert)

       // Insert output will always be a long, even if it's a returning Key (which only happens for inserts btw)
       when(serializer.descriptor.kind) {
         is PrimitiveKind.LONG -> queryOutput.toLong() as Output
         is PrimitiveKind.INT -> queryOutput.toInt() as Output
         is PrimitiveKind.SHORT -> queryOutput.toShort() as Output
         is PrimitiveKind.BYTE -> queryOutput.toByte() as Output
         is PrimitiveKind.CHAR -> queryOutput.toInt().toChar() as Output
         is PrimitiveKind.FLOAT -> queryOutput.toFloat() as Output
         is PrimitiveKind.DOUBLE -> queryOutput.toDouble() as Output
         is PrimitiveKind.STRING -> queryOutput.toString() as Output
         else -> xrError("Invalid serializer descriptor: ${serializer.descriptor}. The returningKeys call needs to be a primitive type when using Sqlite Native.")
       }
    }
  }
