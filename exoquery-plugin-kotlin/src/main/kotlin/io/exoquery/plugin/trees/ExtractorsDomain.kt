package io.exoquery.plugin.trees

import io.decomat.*
import io.exoquery.Query
import io.exoquery.SqlVariable
import io.exoquery.TableQuery
import io.exoquery.plugin.logging.CompileLogger
import io.exoquery.plugin.qualifiedNameForce
import io.exoquery.plugin.safeName
import io.exoquery.plugin.transform.BinaryOperators
import io.exoquery.plugin.transform.UnaryOperators
import io.exoquery.xr.BinaryOperator
import io.exoquery.xr.UnaryOperator
import org.jetbrains.kotlin.ir.backend.js.utils.typeArguments
import org.jetbrains.kotlin.ir.backend.js.utils.valueArguments
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.superTypes

object ExtractorsDomain {
  final val queryClassName = Query::class.qualifiedNameForce

  class QueryFunction(val methodName: String) {
    context (CompileLogger) fun matchesMethod(it: IrCall): Boolean {
      // TODO these can be lazy vals since they all need to be true, so we can have early-exit
      val reciever = it.dispatchReceiver
      val recieverIsQuery =
        reciever?.let { it.type.classFqName.toString() == queryClassName } ?: false
      val recieverSupertypesAreQuery =
        (reciever?.type?.superTypes() ?: listOf()).any { it.classFqName.toString() == queryClassName }
      val methodIsRight = it.symbol.safeName == methodName
      val criteria =
        reciever != null && (recieverIsQuery || recieverSupertypesAreQuery) && methodIsRight && it.simpleValueArgsCount == 1 && it.valueArguments.first() != null

      //error("------------------- MATCHES METHOD: ${criteria} -------------------------\n" + it.dumpKotlinLike())
      return criteria
    }

    data class QueryCallData(val reciver: IrExpression, val functionExpression: IrFunctionExpression, val params:  List<IrValueParameter>, val body: IrBlockBody)

    context (CompileLogger) operator fun <AP: Pattern<QueryCallData>> get(x: AP) =
      customPattern1(x) { it: IrCall ->
        if (matchesMethod(it)) {
          on(it).match(
            // printExpr(.. { stuff }: IrFunctionExpression  ..): FunctionCall
            case( /* .flatMap */ Ir.Call.FunctionMem1[Is(), Is()]).then { reciver, expression ->
              on(expression).match(
                case(Ir.FunctionExpression.withBlock[Is(), Is()]).thenThis { params, blockBody ->
                  val funExpression = this
                  Components1(QueryCallData(reciver, funExpression, params, blockBody))
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
      val TableQueryCompanionFqName = TableQuery.Companion::class.qualifiedName.toString()

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

}