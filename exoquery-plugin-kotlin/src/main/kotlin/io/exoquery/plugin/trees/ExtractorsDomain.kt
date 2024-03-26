package io.exoquery.plugin.trees

import io.decomat.*
import io.exoquery.*
import io.exoquery.plugin.*
import io.exoquery.plugin.logging.CompileLogger
import io.exoquery.plugin.transform.BinaryOperators
import io.exoquery.plugin.transform.Caller
import io.exoquery.plugin.transform.ReceiverCaller
import io.exoquery.plugin.transform.UnaryOperators
import io.exoquery.select.JoinOn
import io.exoquery.select.QueryClause
import io.exoquery.xr.BinaryOperator
import io.exoquery.xr.UnaryOperator
import org.jetbrains.kotlin.ir.backend.js.utils.typeArguments
import org.jetbrains.kotlin.ir.backend.js.utils.valueArguments
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import io.exoquery.plugin.trees.CallData.MultiArgMember.ArgType

// TODO Need to change all instances of FunctionMem... to return Caller as the reciver type
//      so that we can support things like Query<Int>.avg because that inherently needs to
//      be defined as an extension method and the BuilderExtensions.callMethod... needs to know
//      whether it's a dispatch or extension reciver.
//      Probably in the transforms when calling recursive transformations it will be used
//      so it will be necessary to define ReciverCaller.transformWith as well

object ExtractorsDomain {
  final val queryClassName = Query::class.qualifiedNameForce

  sealed interface QueryDslFunction {
    context (CompileLogger) fun matchesMethod(it: IrCall): Boolean
    context (CompileLogger) fun extract(it: IrCall): Pair<RecieverCallData, String>?
  }

  @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
  class LambdaMethodProducingXR: QueryDslFunction {
    context (CompileLogger) override fun matchesMethod(it: IrCall): Boolean =
      // E.g. is Query."map"
      it.simpleValueArgsCount == 1 && it.valueArguments.first() != null && it.isLambdaMethodProducingXR() != null

    context(CompileLogger) override fun extract(expression: IrCall): Pair<CallData.LambdaMember, String>? =
      expression.match(
        // e.g. Query.flatMap[Is()]
        case(Call.LambdaFunctionMethod { matchesMethod(it) }[Is()]).then { queryCallData ->
          expression.isLambdaMethodProducingXR()?.let { method -> queryCallData to method }
        }
      )
  }

  @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
  class MethodProducingXR: QueryDslFunction {
    context (CompileLogger) override fun matchesMethod(it: IrCall): Boolean =
      it.isMethodProducingXR() != null

    context(CompileLogger) override fun extract(expression: IrCall): Pair<CallData.MultiArgMember, String>? =
      expression.match(
        // e.g. Query.flatMap[Is()]
        case(Call.MultiArgMethod { matchesMethod(it) }[Is()]).then { queryCallData ->
          expression.isMethodProducingXR()?.let { method -> queryCallData to method }
        }
      )
  }

  object Call {
    val `groupBy(expr)` = SelectClauseFunction("groupBy")
    val `sortedBy(expr)` = SelectClauseFunction("sortedBy")
    val `where(expr)` = SelectClauseFunction("where")

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

    // A simple one or muti-argument function e.g. take(Int), drop(Int) etc....
    data class MultiArgMethod(val matchesMethod: (IrCall) -> Boolean) {
      context (CompileLogger) operator fun <AP: Pattern<CallData.MultiArgMember>> get(x: AP) =
        customPattern1(x) { it: IrCall ->
          if (matchesMethod(it)) {
            it.match(
              // printExpr(.. { stuff }: IrFunctionExpression  ..): FunctionCall
              case( /* .flatMap */ Ir.Call.FunctionMem[Is(), Is()]).then { reciver, expression ->
                val argTypes =
                  it.symbol.owner.simpleValueParams.map {
                    if (it.isAnnotatedParseXR()) ArgType.ParsedXR else ArgType.Passthrough
                  }

                val parameterComponents = argTypes zip expression
                Components1(CallData.MultiArgMember(reciver, parameterComponents))
              }
            )
          } else null
        }
    }

    object InterpolateInvoke {
      context (CompileLogger) fun matchesMethod(it: IrCall): Boolean =
        it.reciverIs<SqlInterpolator>("invoke") //&& it.simpleValueArgsCount == 2 && it.valueArguments.all{ it != null }

      context (CompileLogger) operator fun <AP: Pattern<IrExpression>, BP: Pattern<List<IrExpression>>> get(reciver: AP, terpComps: BP) =
        customPattern2(reciver, terpComps) { call: IrCall ->
          val caller = call.dispatchReceiver.also { if (it == null) error("Dispatch reciver of the Interpolator invocation `${call.dumpKotlinLike()}` was null. This should not be possible.") }
          if (matchesMethod(call) && caller != null) {
            val x = call.simpleValueArgs.first()
            on(x).match(
              case(Ir.StringConcatenation[Is()]).then { components ->
                Components2(caller, components)
              },
              // TODO does this work?
              // it's a single string-const in this case
//              case(Ir.Const[Is()]).then { const ->
//                Components2(caller, listOf(const))
//              }
            )
          } else {
            null
          }
        }

    }

    // I.e. a bind expression like from/join in a SelectClause
    object QueryClauseAliasedMethod {
      data class Data(val caller: ReceiverCaller, val args: List<IrExpression>, val newMethod: String)

      context (CompileLogger) fun matchesMethod(it: IrCall): Boolean =
        // E.g. is Query."map"
        it.reciverIs<QueryClause<*>>() && it.isQueryClauseMethod() != null

      context (CompileLogger) operator fun <AP: Pattern<Data>> get(x: AP) =
        customPattern1(x) { call: IrCall ->
          if (matchesMethod(call)) {
            on(call).match(
              // (SelectValue).from(innerQuery) <- FunctionMem1, `from` is a member of SelectValue
              case(Ir.Call.FunctionMem[Is(), Is()]).then { reciver, params ->
                call.isQueryClauseMethod()?.let { newMethod ->
                  Components1(Data(reciver, params, newMethod))
                }
              }
            )
          } else null
        }
    }

    object `join-on(expr)` {
      context (CompileLogger) fun matchesMethod(it: IrCall): Boolean =
        // E.g. is Query."map"
        it.reciverIs<JoinOn<*, *, *>>("on") && it.simpleValueArgsCount == 1 && it.valueArguments.first() != null

      context (CompileLogger) operator fun <AP: Pattern<CallData.LambdaMember>> get(x: AP) =
        customPattern1(x) { it: IrCall ->
          if (matchesMethod(it)) {
            on(it).match(
              // (joinClause).on { stuff } <- FunctionMem1, `on` is a member of joinClause
              case(Ir.Call.FunctionMem1[Is(), Is()]).then { reciver, expression ->
                on(expression).match(
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

    class SelectClauseFunction(val methodName: String) {
      context (CompileLogger) fun matchesMethod(it: IrCall): Boolean =
        // E.g. is Query."map"
        it.reciverIs<QueryClause<*>>(methodName) && it.simpleValueArgsCount == 1 && it.valueArguments.first() != null

      context (CompileLogger) operator fun <AP: Pattern<CallData.LambdaMember>> get(x: AP) =
        customPattern1(x) { it: IrCall ->
          if (matchesMethod(it)) {
            on(it).match(
              // (joinClause).on { stuff } <- FunctionMem1, `on` is a member of joinClause
              case(Ir.Call.FunctionMem1[Is(), Is()]).then { reciver, expression ->
                on(expression).match(
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

    object `x op y` {
      context (CompileLogger) operator fun <AP: Pattern<OperatorCall>> get(x: AP) =
        customPattern1(x) { it: IrCall ->
          on(it).match(
            case(Ir.Call.FunctionUntethered2[Is(), Is()]).thenThis { arg1, arg2 -> Triple(this.symbol.safeName, arg1, arg2) },
            case(Ir.Call.FunctionRec1[Is(), Is()]).thenThis { arg1, arg2 -> Triple(this.symbol.safeName, arg1, arg2) }
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
            case(Ir.Call.FunctionUntethered1[Is()]).thenThis { arg1 -> Pair(this.symbol.safeName, arg1) },
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
              case( /* .flatMap */ Ir.Call.FunctionUntethered1[Is()]).then { expression ->
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

    object InvokeSqlVariable {
      private val SqlVariableFqName = SqlVariable::class.qualifiedName.toString()

      context (CompileLogger) operator fun <AP: Pattern<String>> get(statements: AP) =
        customPattern1(statements) { it: IrCall ->
          on(it).match(
            case(Ir.Call.FunctionMem0[ReceiverCaller[Is<IrGetValue>()]]).thenThis { (getValue) ->
              when {
                getValue.type.classFqName.toString() == SqlVariableFqName && this.symbol.safeName == "invoke" ->
                  Components1(getValue.symbol.safeName)
                else -> null
              }
            }
          )
        }
    }

    object MakeTable {
      val TableQueryCompanionFqName = Table.Companion::class.qualifiedName.toString()

      context (CompileLogger) operator fun <AP: Pattern<IrType>> get(statements: AP) =
        customPattern1(statements) { it: IrCall ->
          on(it).match(
            case(Ir.Call.FunctionMem0[ReceiverCaller[Is<IrGetObjectValue>()]]).thenThis { (getObject) ->
                when {
                  getObject.type.classFqName.toString() == TableQueryCompanionFqName && type.classOrNull != null -> {
                    val firstArg = this.typeArguments.first()
                    if (firstArg == null) kotlin.error("First type-argument to the TableQuery call was null. This should be impossible.")
                    else Components1(firstArg as IrType)
                  }
                  else -> null
                }
              }
          )
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