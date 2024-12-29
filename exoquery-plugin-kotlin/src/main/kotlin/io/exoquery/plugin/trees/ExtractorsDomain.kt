package io.exoquery.plugin.trees

import io.decomat.*
import io.exoquery.*
import io.exoquery.plugin.*
import io.exoquery.plugin.logging.CompileLogger
import io.exoquery.plugin.printing.dumpSimple
import io.exoquery.plugin.transform.BinaryOperators
import io.exoquery.plugin.transform.Caller
import io.exoquery.plugin.transform.ReceiverCaller
import io.exoquery.plugin.transform.UnaryOperators
import io.exoquery.xr.BinaryOperator
import io.exoquery.xr.UnaryOperator
import org.jetbrains.kotlin.ir.backend.js.utils.typeArguments
import org.jetbrains.kotlin.ir.backend.js.utils.valueArguments
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import io.exoquery.plugin.trees.CallData.MultiArgMember.ArgType
import org.jetbrains.kotlin.ir.types.classFqName

// TODO Need to change all instances of FunctionMem... to return Caller as the reciver type
//      so that we can support things like Query<Int>.avg because that inherently needs to
//      be defined as an extension method and the BuilderExtensions.callMethod... needs to know
//      whether it's a dispatch or extension reciver.
//      Probably in the transforms when calling recursive transformations it will be used
//      so it will be necessary to define ReciverCaller.transformWith as well

object ExtractorsDomain {

  sealed interface QueryDslFunction {
    context (CompileLogger) fun matchesMethod(it: IrCall): Boolean
    context (CompileLogger) fun extract(it: IrCall): Pair<RecieverCallData, ReplacementMethodToCall>?
  }

  object CaseClassConstructorCall {
    data class Data(val className: String, val fields: List<Field>)
    data class Field(val name: String, val value: IrExpression?)

    context (CompileLogger) operator fun <AP: Pattern<Data>> get(x: AP) =
      customPattern1(x) { call: IrConstructorCall ->
        when {
          call.symbol.safeName == "<init>" -> {
            val className: String = call.type.classFqName?.asString() ?: call.type.dumpKotlinLike()
            if (!call.symbol.owner.isPrimary)
              parseError("Detected construction of the class ${className} using a non-primary constructor. This is not allowed.")

            val params = call.symbol.owner.simpleValueParams.map { it.name.asString() }.toList()
            val args = call.valueArguments.toList()
            if (params.size != args.size)
              parseError("Cannot parse constructor of ${className} its params ${params} do not have the same cardinality as its arguments ${args.map { it?.dumpKotlinLike() }}")
            val fields = (params zip args).map { (name, value) -> Field(name, value)}
            Components1(Data(className, fields))
          }
          else -> null
        }
      }
  }

  object CaseClassConstructorCall1 {
    context (CompileLogger) operator fun <AP: Pattern<String>, BP: Pattern<IrExpression>> get(x: AP, y: BP) =
      customPattern2(x, y) { call: IrConstructorCall ->
        when {
          call.symbol.safeName == "<init>" && call.symbol.owner.simpleValueParams.size == 1 -> {
            val className: String = call.type.classFqName?.asString() ?: call.type.dumpKotlinLike()
            if (!call.symbol.owner.isPrimary)
              parseError("Detected construction of the class ${className} using a non-primary constructor. This is not allowed.")

            val params = call.symbol.owner.simpleValueParams.map { it.name.asString() }.toList()
            val args = call.valueArguments.toList()
            if (params.size != args.size)
              parseError("Cannot parse constructor of ${className} its params ${params} do not have the same cardinality as its arguments ${args.map { it?.dumpKotlinLike() }}")
            Components2(className, args.first())
          }
          else -> null
        }
      }
  }

  // Match case classes that have at least one paramater. The match is on the case class name and the first parameter. This is useful
  // since in many cases the primary deconstructin logic is on on that data
  object CaseClassConstructorCall1Plus {
    context (CompileLogger) operator fun <AP: Pattern<String>, BP: Pattern<IrExpression>> get(x: AP, y: BP) =
      customPattern2(x, y) { call: IrConstructorCall ->
        when {
          call.symbol.safeName == "<init>" && call.symbol.owner.simpleValueParams.size >= 1 -> {
            val className: String = call.type.classFqName?.asString() ?: call.type.dumpKotlinLike()
            if (!call.symbol.owner.isPrimary)
              parseError("Detected construction of the class ${className} using a non-primary constructor. This is not allowed.")

            val params = call.symbol.owner.simpleValueParams.map { it.name.asString() }.toList()
            val args = call.valueArguments.toList()
            if (params.size != args.size)
              parseError("Cannot parse constructor of ${className} its params ${params} do not have the same cardinality as its arguments ${args.map { it?.dumpKotlinLike() }}")
            Components2(className, args.first())
          }
          else -> null
        }
      }
  }

  object Call {
    data class OperatorCall(val x: IrExpression, val op: BinaryOperator, val y: IrExpression)
    data class UnaryOperatorCall(val x: IrExpression, val op: UnaryOperator)

    data class LambdaFunctionMethod(val matchesMethod: (IrCall) -> Boolean) {
      context (CompileLogger) operator fun <AP: Pattern<CallData.LambdaMember>> get(x: AP) =
        customPattern1(x) { it: IrCall ->
          if (matchesMethod(it)) {
            it.match(
              // printExpr(.. { stuff }: IrFunctionExpression  ..): FunctionCall
              case( /* .flatMap */ Ir.Call.FunctionMem1[Is(), Is()]).then { reciver, expression ->
                expression.match(
                  case(Ir.FunctionExpression.withBlock[Is(), Is()]).thenThis { params, blockBody ->
                    val funExpression = this
                    Components1(CallData.LambdaMember(reciver, funExpression, params, blockBody))
                  }
                )
              }
            )
          } else null
        }
    }


    // (SqlExpression<*>.asQuery)
    // (SqlExpression<*>.asValue)
    // (SqlExpression<*>.invoke)

    object `x op y` {
      context (CompileLogger) operator fun <AP: Pattern<OperatorCall>> get(x: AP) =
        customPattern1(x) { it: IrCall ->
          it.match(
            case(Ir.Call.FunctionUntethered2[Is(), Is()])
              .thenIfThis { _, _ -> BinaryOperators.operators.get(symbol.safeName) != null }
              .thenThis { arg1, arg2 -> Triple(this.symbol.safeName, arg1, arg2) },
            case(Ir.Call.FunctionRec1[Is(), Is()])
              .thenIfThis { _, _ -> BinaryOperators.operators.get(symbol.safeName) != null }
              .thenThis { arg1, arg2 -> Triple(this.symbol.safeName, arg1, arg2) }
          )?.let { result ->
            val (opName, arg1, arg2) = result
            BinaryOperators.operators.get(opName)?.let { op ->
              Components1(OperatorCall(arg1, op, arg2))
            }
          }
        }
    }

    inline fun <T> Boolean.thenLet(predicate: () -> T): T? =
      if (this) predicate() else null

    object `x to y` {
      context (CompileLogger) operator fun <AP: Pattern<IrExpression>, BP: Pattern<IrExpression>> get(x: AP, y: BP) =
        customPattern2(x, y) { it: IrCall ->
          // TODO see what other descriptors it has to make sure it's only a system-level a to b
          (it.symbol.safeName == "to").thenLet {
            it.extensionReceiver?.let { argA ->
              it.simpleValueArgs.first()?.let { argB ->
                Components2(argA, argB)
              }
            }
          }
        }
    }


    object `(op)x` {
      context (CompileLogger) operator fun <AP: Pattern<UnaryOperatorCall>> get(x: AP) =
        customPattern1(x) { it: IrCall ->
          on(it).match(
            case(Ir.Call.FunctionUntethered1.Arg[Is()]).thenThis { arg1 -> Pair(this.symbol.safeName, arg1) },
            case(Ir.Call.FunctionRec0[Is()]).thenThis { arg1 -> Pair(this.symbol.safeName, arg1) }
          )?.let { result ->
            val (opName, arg1) = result
            UnaryOperators.operators.get(opName)?.let { op ->
              Components1(UnaryOperatorCall(arg1, op))
            }
          }
        }
    }

    object `select(fun)` {
      context (CompileLogger) fun matchesMethod(it: IrCall): Boolean =
        // E.g. is Query."map"
        it.dispatchReceiver == null && it.extensionReceiver == null && it.symbol.safeName == "select" && it.simpleValueArgsCount == 1 && it.valueArguments.first() != null

      context (CompileLogger) operator fun <AP: Pattern<CallData.LambdaTopLevel>> get(x: AP) =
        customPattern1(x) { it: IrCall ->
          if (matchesMethod(it)) {
            on(it).match(
              // printExpr(.. { stuff }: IrFunctionExpression  ..): FunctionCall
              case( /* .flatMap */ Ir.Call.FunctionUntethered1.Arg[Is()]).then { expression ->
                on(expression).match(
                  case(Ir.FunctionExpression.withBlock[Is(), Is()]).thenThis { params, blockBody ->
                    val funExpression = this
                    Components1(CallData.LambdaTopLevel(funExpression, params, blockBody))
                  }
                )
              }
            )
          } else null
        }
    }
  }


}

sealed interface RecieverCallData: CallData
sealed interface CallData {
  // i.e. Something.someMethod { someLambda }
  data class LambdaMember(val reciver: ReceiverCaller, val functionExpression: IrFunctionExpression, val params:  List<IrValueParameter>, val body: IrBlockBody): RecieverCallData
  // i.e. someMethod { someLambda }
  data class LambdaTopLevel(val functionExpression: IrFunctionExpression, val params:  List<IrValueParameter>, val body: IrBlockBody): CallData

  data class MultiArgMember(val reciver: ReceiverCaller, val argValues: List<Pair<ArgType, IrExpression>>): RecieverCallData {
    sealed interface ArgType {
      object ParsedXR: ArgType
      object Passthrough: ArgType
    }
  }
}