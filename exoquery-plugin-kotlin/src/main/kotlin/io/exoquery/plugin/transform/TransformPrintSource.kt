package io.exoquery.plugin.transform

import io.decomat.Is
import io.decomat.case
import io.decomat.match
import io.decomat.on
import io.exoquery.parseError
import io.exoquery.plugin.trees.Ir
import io.exoquery.plugin.logging.CompileLogger
import io.exoquery.plugin.logging.Messages
import io.exoquery.plugin.printing.dumpSimple
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

class TransformPrintSource(
  private val context: BuilderContext
) {
  private val compileLogger = context.logger

  private val printSourceFqn: String = "io.exoquery.printSource"

  fun matches(expression: IrCall): Boolean =
    expression.symbol.owner.kotlinFqName.asString()
      .let { it == printSourceFqn }

  sealed interface MatchedType {
    data class Multi(val irs: List<IrStatement>): MatchedType
    data class Single(val ir: IrExpression): MatchedType
  }

  fun transform(expression: IrCall): IrExpression {
    val args =
      with(compileLogger) {
        on(expression).match(
          case(Ir.Call.FunctionUntethered1.Arg[Ir.FunctionExpression.withReturnOnlyBlock[Is()]]).then { (ret) ->

            //ret.match(
            //  case(Ir.Call.FunctionMem0[Ir.Type.ClassOf<io.exoquery.Runtimes.Companion>(), Is("Empty")]).then { expr, _ ->
            //    error("================== Matched Call to Empty ==================\n" + expr.dumpKotlinLike() + "\n--------------------------\n" + expr.dumpSimple())
            //  },
            //)

            MatchedType.Single(ret)
          },
          // printExpr(.. { stuff }: IrFunctionExpression  ..): FunctionCall
          case(Ir.Call.FunctionUntethered1.Arg[Ir.FunctionExpression.withBlockStatements[Is(), Is()]]).then { (_, args) ->
            MatchedType.Multi(args)
          }
        )
      } ?: parseError("Parsing Failed\n================== The expresson was not a Global Function (with one argument-block): ==================\n" + expression.dumpKotlinLike() + "\n--------------------------\n" + expression.dumpSimple())

    val printSourceExpr = context.pluginCtx
      .referenceFunctions(
        CallableId(FqName("io.exoquery"), Name.identifier("printSourceExpr"))
      ).first()

    val message = when(args) {
      is MatchedType.Single -> Messages.PrintingMessageSingle(args.ir, "Single Return")
      is MatchedType.Multi -> Messages.PrintingMessageMulti(args.irs, "Multi Return Statements")
    }

    compileLogger.warn(message)

    return with(context.builder) {
      this.irCall(printSourceExpr).apply {
        putValueArgument(0, irString(message))
      }
    }
  }


}