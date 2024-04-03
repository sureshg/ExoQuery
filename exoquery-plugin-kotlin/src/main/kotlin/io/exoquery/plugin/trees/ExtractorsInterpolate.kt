package io.exoquery.plugin.trees

import io.decomat.*
import io.exoquery.SQL
import io.exoquery.parseError
import io.exoquery.plugin.isClass
import io.exoquery.plugin.logging.CompileLogger
import io.exoquery.plugin.printing.dumpSimple
import io.exoquery.plugin.reciverIs
import io.exoquery.plugin.safeName
import io.exoquery.terpal.Interpolator
import io.exoquery.terpal.InterpolatorFunction
import org.jetbrains.kotlin.ir.backend.js.utils.asString
import org.jetbrains.kotlin.ir.backend.js.utils.valueArguments
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.getConstructorTypeArguments
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.isSubtypeOfClass
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.ir.util.isObject
import org.jetbrains.kotlin.ir.util.superTypes

object ExtractorsInterpolate {
  object Call {
    object InterpolateInvoke {
      context (CompileLogger) fun matchesMethod(it: IrCall): Boolean {
        val out = it.reciverIs<SQL>("invoke")
        //if (out) error("------------------ Matching invoke: ${it.dumpKotlinLike()}")
        return out //&& it.simpleValueArgsCount == 2 && it.valueArguments.all{ it != null }
      }

      context (CompileLogger) operator fun <AP: Pattern<IrExpression>, BP: Pattern<List<IrExpression>>> get(reciver: AP, terpComps: BP) =
        customPattern2(reciver, terpComps) { call: IrCall ->
          val firstArg = call.simpleValueArgs.first()
          if (matchesMethod(call) && firstArg != null) {
            val caller: IrExpression = call.dispatchReceiver ?: parseError("Dispatch reciver of the Interpolator invocation `${call.dumpKotlinLike()}` was null. This should not be possible.")
            // Don't need to do any more sophisticated matching (e.g. what kind of `invoke` method it is because the invoke(string) only allows strings hence i.e. no generics
            // and the invoke(() -> String) only allows string-returning lambdas. Hence there's no possibility of ambiguity here.
            firstArg.match(
              case(Ir.FunctionExpression.withBlock[Is(), Is()]).then { _, blockBody ->
                // since lambda is actually FunctionExpression(IrBlockBody(IrReturn))
                blockBody.match(
                  case(Ir.ReturnBlockInto[Ir.StringConcatenation[Is()]]).then { (components) ->
                    Components2(caller, components)
                  },
                  case(Ir.ReturnBlockInto[Ir.Const[Is()]]).thenThis { _ ->
                    Components2(caller, listOf(this))
                  }
                )
              },

              case(Ir.StringConcatenation[Is()]).then { components ->
                Components2(caller, components)
              },
              // it's a single string-const in this case
              case(Ir.Const[Is()]).thenThis { _ ->
                Components2(caller, listOf(this))
              }
            )
          } else {
            null
          }
        }

    }
  }
}