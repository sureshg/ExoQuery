package io.exoquery.plugin.trees

import io.decomat.*
import io.exoquery.Query
import io.exoquery.SqlVariable
import io.exoquery.Table
import io.exoquery.plugin.logging.CompileLogger
import io.exoquery.plugin.qualifiedNameForce
import io.exoquery.plugin.safeName
import io.exoquery.plugin.transform.BinaryOperators
import io.exoquery.plugin.transform.UnaryOperators
import io.exoquery.select.JoinOn
import io.exoquery.select.SelectClause
import io.exoquery.xr.BinaryOperator
import io.exoquery.xr.UnaryOperator
import org.jetbrains.kotlin.ir.backend.js.utils.typeArguments
import org.jetbrains.kotlin.ir.backend.js.utils.valueArguments
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.superTypes

inline fun <reified T> IrExpression.isClass(): Boolean {
  val className = T::class.qualifiedNameForce
  return className == this.type.classFqName.toString() || type.superTypes().any { it.classFqName.toString() == className }
}

inline fun <reified T> IrType.isClass(): Boolean {
  val className = T::class.qualifiedNameForce
  return className == this.classFqName.toString() || this.superTypes().any { it.classFqName.toString() == className }
}

inline fun <reified T> IrCall.reciverIs(methodName: String) =
  this.dispatchReceiver?.isClass<T>() ?: false && this.symbol.safeName == methodName

object ExtractorsDomain {
  final val queryClassName = Query::class.qualifiedNameForce

  class QueryFunction(val methodName: String) {
    context (CompileLogger) fun matchesMethod(it: IrCall): Boolean =
      // E.g. is Query."map"
      it.reciverIs<Query<*>>(methodName) && it.simpleValueArgsCount == 1 && it.valueArguments.first() != null

    context (CompileLogger) operator fun <AP: Pattern<CallData>> get(x: AP) =
      customPattern1(x) { it: IrCall ->
        if (matchesMethod(it)) {
          on(it).match(
            // printExpr(.. { stuff }: IrFunctionExpression  ..): FunctionCall
            case( /* .flatMap */ Ir.Call.FunctionMem1[Is(), Is()]).then { reciver, expression ->
              on(expression).match(
                case(Ir.FunctionExpression.withBlock[Is(), Is()]).thenThis { params, blockBody ->
                  val funExpression = this
                  Components1(CallData(reciver, funExpression, params, blockBody))
                }
              )
            }
          )
        } else null
      }
  }

  object Call {
    val QueryMap = QueryFunction("map")
    val QueryFlatMap = QueryFunction("flatMap")

    data class OperatorCall(val x: IrExpression, val op: BinaryOperator, val y: IrExpression)
    data class UnaryOperatorCall(val x: IrExpression, val op: UnaryOperator)

    // TODO refactor in to SelectClause function to include join & other things?
    object `from(expr)` {
      context (CompileLogger) fun matchesMethod(it: IrCall): Boolean =
        // E.g. is Query."map"
        it.reciverIs<SelectClause<*>>("from") && it.simpleValueArgsCount == 1 && it.valueArguments.first() != null

      context (CompileLogger) operator fun <AP: Pattern<IrExpression>, BP: Pattern<IrExpression>> get(x: AP, y: BP) =
        customPattern2(x, y) { it: IrCall ->
          if (matchesMethod(it)) {
            on(it).match(
              // (SelectValue).from(innerQuery) <- FunctionMem1, `from` is a member of SelectValue
              case(Ir.Call.FunctionMem1[Is(), Is()]).then { reciver, expression ->
                Components2(reciver, expression)
              }
            )
          } else null
        }
    }

    object `join-on(expr)` {
      context (CompileLogger) fun matchesMethod(it: IrCall): Boolean =
        // E.g. is Query."map"
        it.reciverIs<JoinOn<*, *, *>>("on") && it.simpleValueArgsCount == 1 && it.valueArguments.first() != null

      context (CompileLogger) operator fun <AP: Pattern<CallData>> get(x: AP) =
        customPattern1(x) { it: IrCall ->
          if (matchesMethod(it)) {
            on(it).match(
              // (joinClause).on { stuff } <- FunctionMem1, `on` is a member of joinClause
              case(Ir.Call.FunctionMem1[Is(), Is()]).then { reciver, expression ->
                on(expression).match(
                  case(Ir.FunctionExpression.withBlock[Is(), Is()]).thenThis { params, blockBody ->
                    val funExpression = this
                    Components1(CallData(reciver, funExpression, params, blockBody))
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

    object InvokeSqlVariable {
      private val SqlVariableFqName = SqlVariable::class.qualifiedName.toString()

      context (CompileLogger) operator fun <AP: Pattern<String>> get(statements: AP) =
        customPattern1(statements) { it: IrCall ->
          on(it).match(
            case(Ir.Call.FunctionMem0[Is<IrGetValue>()]).thenThis { getValue ->
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
            case(Ir.Call.FunctionMem0[Is<IrGetObjectValue>()]).thenThis { getObject ->
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

  data class CallData(val reciver: IrExpression, val functionExpression: IrFunctionExpression, val params:  List<IrValueParameter>, val body: IrBlockBody)
}