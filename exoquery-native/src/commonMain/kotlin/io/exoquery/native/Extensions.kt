package io.exoquery.native

import io.exoquery.ActionKind
import io.exoquery.ActionReturningKind
import io.exoquery.Param
import io.exoquery.ParamBatchRefiner
import io.exoquery.ParamMulti
import io.exoquery.ParamSingle
import io.exoquery.SqlCompiledAction
import io.exoquery.SqlCompiledQuery
import io.exoquery.controller.ControllerAction
import io.exoquery.controller.ControllerActionReturning
import io.exoquery.controller.Messages
import io.exoquery.xrError
import io.exoquery.controller.ControllerQuery
import io.exoquery.controller.StatementParam
import io.exoquery.controller.native.NativeDatabaseController
import io.exoquery.controller.runOn
import io.exoquery.illegalOp
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.serializer

// Change testing-controller to controller-common and it in there?
internal fun <T : Any> Param<T>.toStatementParam(): StatementParam<T> =
  when (this) {
    is ParamBatchRefiner<*, *> ->
      xrError("Attempted to convert batch-param refiner to a database-statement parameter. This is illegal, all batch-param refiners need to be converted into normal Param instances first. The incorrect refiner was:\n${this.description}")
    is ParamMulti<*> ->
      xrError("Attempted to convert multi-param to a database-statement parameter. This is illegal, all multi-params need to be converted into normal Param instances first. The incorrect param was:\n${this.description}")
    is ParamSingle<*> ->
      StatementParam<T>(this.serial.serializer, this.serial.cls, value as T)
  }

internal fun <T> SqlCompiledQuery<T>.toControllerQuery(serializer: KSerializer<T>): ControllerQuery<T> =
  ControllerQuery(token.build(), params.map { it.toStatementParam() }, serializer)

suspend fun <T> SqlCompiledQuery<T>.runOn(database: NativeDatabaseController, serializer: KSerializer<T>) =
  this.toControllerQuery(serializer).runOn(database)

inline suspend fun <reified T : Any> SqlCompiledQuery<T>.runOn(database: NativeDatabaseController) =
  this.runOn(database, serializer())


suspend fun <Input, Output> SqlCompiledAction<Input, Output>.runOn(database: NativeDatabaseController, serializer: KSerializer<Output>): Output =
  when (actionReturningKind) {
    is ActionReturningKind.None -> {
      val action = ControllerAction(token.build(), params.map { it.toStatementParam() })
      // Check the kind of "Output" i.e. it needs to be a Long (we can use the descriptor-kind as a proxy for this and not need to pass a KClass in)
      if (serializer.descriptor.kind == PrimitiveKind.LONG)
        action.runOn(database) as Output
      else
        illegalOp("The action is not returning anything, but the serializer is not a Long. This is illegal. The serializer was:\n${serializer.descriptor}")
    }
    is ActionReturningKind.ClauseInQuery -> {
      // Try not passing the keys explicitly? If it's a action-returning do we need them?
      val actionReturning = ControllerActionReturning.Row(value, params.map { it.toStatementParam() }, serializer, listOf())
      actionReturning.runOn(database)
    }
    is ActionReturningKind.Keys -> {
      val actionReturningId = ControllerActionReturning.Id(value, params.map { it.toStatementParam() }, Long.serializer(), (actionReturningKind as ActionReturningKind.Keys).columns)
      val queryOutput = actionReturningId.runOn(database)

      if (actionKind != ActionKind.Insert)
        illegalOp(MessagesNative.SqliteNativeCantReturningKeysIfNotInsert)

      // Insert output will always be a long, even if it's a returning Key (which only happens for inserts btw)
      when (serializer.descriptor.kind) {
        is PrimitiveKind.LONG -> queryOutput.toLong() as Output
        is PrimitiveKind.INT -> queryOutput.toInt() as Output
        is PrimitiveKind.SHORT -> queryOutput.toShort() as Output
        is PrimitiveKind.BYTE -> queryOutput.toByte() as Output
        is PrimitiveKind.CHAR -> queryOutput.toChar() as Output
        is PrimitiveKind.FLOAT -> queryOutput.toFloat() as Output
        is PrimitiveKind.DOUBLE -> queryOutput.toDouble() as Output
        is PrimitiveKind.STRING -> queryOutput.toString() as Output
        else -> xrError("Invalid serializer descriptor: ${serializer.descriptor}. The returningKeys call needs to be a primitive type when using Sqlite Native.")
      }
    }
  }

inline suspend fun <Input, reified Output> SqlCompiledAction<Input, Output>.runOn(database: NativeDatabaseController) =
  this.runOn(database, serializer<Output>())
