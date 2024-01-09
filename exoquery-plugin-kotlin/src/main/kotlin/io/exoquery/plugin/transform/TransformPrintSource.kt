package io.exoquery.plugin.transform

import io.decomat.Is
import io.decomat.case
import io.decomat.on
import io.exoquery.plugin.trees.Ir
import io.exoquery.plugin.logging.CompileLogger
import io.exoquery.plugin.logging.Messages
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

class TransformPrintSource(
  private val context: IrPluginContext,
  private val config: CompilerConfiguration,
  private val scopeOwner: IrSymbol
) {
  private val compileLogger = CompileLogger(config)

  private val printSourceFqn: String = "io.exoquery.printSource"

  fun matches(expression: IrCall): Boolean =
    expression.symbol.owner.kotlinFqName.asString()
      .let { it == printSourceFqn }

  fun transform(expression: IrCall): IrExpression {
    val args =
      with(compileLogger) {
        on(expression).match(
          // printExpr(.. { stuff }: IrFunctionExpression  ..): FunctionCall
          case(Ir.Call.FunctionUntethered1[Ir.FunctionExpression.withBlockStatements[Is(), Is()]]).then { (_, args) ->
            args
          }
        )
      }

    val printSourceExpr = context
      .referenceFunctions(
        CallableId(FqName("io.exoquery.exoquery"), Name.identifier("printSourceExpr"))
      ).first()

    val message = Messages.PrintingMessageMulti(args)

    compileLogger.warn(message)

    val irBuilder = DeclarationIrBuilder(context, scopeOwner, expression.startOffset, expression.endOffset)
    return with(irBuilder) {
      this.irCall(printSourceExpr).apply {
        putValueArgument(0, irBuilder.irString(message))
      }
    }
  }


}