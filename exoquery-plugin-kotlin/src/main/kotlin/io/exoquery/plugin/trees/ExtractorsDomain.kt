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
    context (CompileLogger) fun extract(it: IrCall): Pair<RecieverCallData, ReplacementMethodToCall>?
  }

  @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
  class LambdaMethodProducingXR: QueryDslFunction {
    context (CompileLogger) override fun matchesMethod(it: IrCall): Boolean =
      // E.g. is Query."map"
      it.simpleValueArgsCount == 1 && it.valueArguments.first() != null && it.markedLambdaMethodProducingXR() != null

    context(CompileLogger) override fun extract(expression: IrCall): Pair<CallData.LambdaMember, ReplacementMethodToCall>? =
      expression.match(
        // e.g. Query.flatMap[Is()]
        case(Call.LambdaFunctionMethod { matchesMethod(it) }[Is()]).then { queryCallData ->
          expression.markedLambdaMethodProducingXR()?.let { annotationData -> queryCallData to annotationData }
        }
      )
  }

  @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
  class MethodProducingXR: QueryDslFunction {
    context (CompileLogger) override fun matchesMethod(it: IrCall): Boolean =
      it.markedMethodProducingXR() != null

    context(CompileLogger) override fun extract(expression: IrCall): Pair<CallData.MultiArgMember, ReplacementMethodToCall>? =
      expression.match(
        // e.g. Query.flatMap[Is()]
        case(Call.MultiArgMethod { matchesMethod(it) }[Is()]).then { queryCallData ->
          expression.markedMethodProducingXR()?.let { annotationData -> queryCallData to annotationData }
        }
      )
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

    // TODO extract out common parts of this in QueryClauseAliasedMethod
    object QueryClauseDirectMethod {
      data class Data(val caller: ReceiverCaller, val args: List<IrExpression>, val replacementMethodToCall: ReplacementMethodToCall)

      context (CompileLogger) fun matchesMethod(it: IrCall): Boolean =
        // E.g. is Query."map"
        (it.reciverIs<QueryClause<*>>() || it.reciverIs<JoinOn<*, *, *>>()) && it.markedQueryClauseDirectMethod() != null

      context (CompileLogger) operator fun <AP: Pattern<Data>> get(x: AP) =
        customPattern1(x) { call: IrCall ->
          if (matchesMethod(call)) {
            on(call).match(
              // (SelectValue).from(innerQuery) <- FunctionMem1, `from` is a member of SelectValue
              case(Ir.Call.FunctionMem[Is(), Is()]).then { reciver, params ->
                call.markedQueryClauseDirectMethod()?.let { annotationData ->
                  Components1(Data(reciver, params, annotationData))
                }
              }
            )
          } else null
        }
    }

    // I.e. a bind expression like from/join in a SelectClause
    object QueryClauseAliasedMethod {
      data class Data(val caller: ReceiverCaller, val args: List<IrExpression>, val replacementMethodToCall: ReplacementMethodToCall)

      context (CompileLogger) fun matchesMethod(it: IrCall): Boolean =
        // E.g. is Query."map"
        it.reciverIs<QueryClause<*>>() && it.markedQueryClauseAliasedMethod() != null

      context (CompileLogger) operator fun <AP: Pattern<Data>> get(x: AP) =
        customPattern1(x) { call: IrCall ->
          if (matchesMethod(call)) {
            on(call).match(
              // (SelectValue).from(innerQuery) <- FunctionMem1, `from` is a member of SelectValue
              case(Ir.Call.FunctionMem[Is(), Is()]).then { reciver, params ->
                call.markedQueryClauseAliasedMethod()?.let { newMethod ->
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

    object QueryClauseUnitBindMethod {
      data class Data(val caller: ReceiverCaller, val params: List<IrValueParameter>, val blockBody: IrBlockBody, val replacementMethodToCall: ReplacementMethodToCall)

      context (CompileLogger) fun matchesMethod(it: IrCall): Boolean =
        // E.g. is Query."map"
        it.reciverIs<QueryClause<*>>() && it.markedQueryClauseUnitBind() != null

      context (CompileLogger) operator fun <AP: Pattern<Data>> get(x: AP) =
        customPattern1(x) { it: IrCall ->
          if (matchesMethod(it)) {
            on(it).match(
              // (joinClause).on { stuff } <- FunctionMem1, `on` is a member of joinClause
              case(Ir.Call.FunctionMem1[Is(), Is()]).then { reciver, expression ->
                expression.match(
                  case(Ir.FunctionExpression.withBlock[Is(), Is()]).thenThis { params, blockBody ->
                    it.markedQueryClauseUnitBind()?.let { method -> Components1(Data(reciver, params, blockBody, method)) }
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

    object InvokeQuery {
      context (CompileLogger) operator fun <AP: Pattern<ReceiverCaller>> get(statements: AP) =
        customPattern1(statements) { it: IrCall ->
          on(it).match(
            case(Ir.Call.FunctionMem0[Is()]).thenThis { caller ->
              val callerExpr = caller.reciver
              when {
                callerExpr.type.isClass<Query<*>>() && (this.symbol.safeName == "invoke" || this.symbol.safeName == "value") -> {
                  // validate that the caller is a dispatch
                  when (caller) {
                    is Caller.Dispatch -> parseError("Reciever of a SqlExpression invocation was a dispatch, this should not be possible. SqlExpression.invoke should be a extension method. Error occured: ${it.dumpKotlinLike()}")
                    else -> {}
                  }
                  Components1(caller)
                }
                else -> null
              }
            }
          )
        }
    }

    object InvokeSqlExpression {
      private val SqlVariableFqName = SqlVariable::class.qualifiedName.toString()

      context (CompileLogger) operator fun <AP: Pattern<ReceiverCaller>> get(statements: AP) =
        customPattern1(statements) { it: IrCall ->
          on(it).match(
            case(Ir.Call.FunctionMem0[Is()]).thenThis { caller ->
              val callerExpr = caller.reciver
              when {
                callerExpr.type.isClass<SqlExpression<*>>() && (this.symbol.safeName == "invoke" || this.symbol.safeName == "value") -> {
                  // validate that the caller is a dispatch
                  when (caller) {
                    is Caller.Dispatch -> parseError("Reciever of a SqlExpression invocation was a dispatch, this should not be possible. SqlExpression.invoke should be a extension method. Error occured: ${it.dumpKotlinLike()}")
                    else -> {}
                  }
                  Components1(caller)
                }
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