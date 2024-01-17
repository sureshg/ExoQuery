package io.exoquery.plugin.transform

import io.decomat.Is
import io.decomat.case
import io.decomat.on
import io.exoquery.EntityExpression
import io.exoquery.xr.XR
import io.exoquery.plugin.logging.CompileLogger
import io.exoquery.plugin.printing.DomainErrors
import io.exoquery.plugin.safeName
import io.exoquery.plugin.trees.*
import io.exoquery.xr.XRType
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.ir.util.functions

class TransformTableQuery(val ctx: TransformerOrigin) {
  private val compileLogger = CompileLogger(ctx.config)

  fun matches(expression: IrCall): Boolean = run {
    with(compileLogger) {
      on(expression).match(
        case(ExtractorsDomain.Call.MakeTable[Is()]).then { true }
      ) ?: false
    }
  }

  fun lifterAndBuilder(expr: IrCall): Pair<Lifter, DeclarationIrBuilder> {
    val builderCtx = ctx.makeBuilderContext(expr)
    val builder = builderCtx.builder //DeclarationIrBuilder(context, scopeOwner, expr.startOffset, expr.endOffset)
    val lifter = Lifter(builderCtx)
    return Pair(lifter, builder)
  }

  fun IrType.classOrFail(msg: String): IrClassSymbol {
    val cls = this.classOrNull
    return when {
      cls == null -> kotlin.error("$msg. Could not get the a class symbol from the type: ${this.dumpKotlinLike()}")
      else -> cls
    }
  }

  fun transform(expression: IrCall): IrExpression =
    // Technically we don't care about any scope-symbols in here but propagate them in case we might want then in the future
    with (ParserContext(ctx.parentScopeSymbols, ctx.currentFile)) {
      with (compileLogger) {
        transformInternal(expression)
      }
    }

  fun XRType.productOrFail(originalType: IrType): XRType.Product =
    when(this) {
      is XRType.Product -> this
      else -> DomainErrors.NotProductTypeParsedFromType(this, originalType)

    }

  context(ParserContext, CompileLogger) fun transformInternal(expression: IrCall): IrExpression =
    on(expression).match(
      case(ExtractorsDomain.Call.MakeTable[Is()]).thenThis { entityClass ->
        val xrType = TypeParser.parse(entityClass).productOrFail(entityClass)
        val xr = XR.Entity(entityClass.classOrFail("Error derving class of TableQuery").safeName, xrType)
        val (lifter, builder) = lifterAndBuilder(expression)
        val caller = this.dispatchReceiver ?: kotlin.error("Dispatch reciever of the following expression was null. This should not be possible:\n" + expression.dumpKotlinLike())

        val entityExpression = EntityExpression(xr)
        val liftedEntity = lifter.liftExpression(entityExpression)

        val fromExprFunction =
          caller.type.classOrFail("Error looking up class info from ${caller.dumpKotlinLike()} during TableQuery deduction")
            .functions
            .find { it.safeName == "fromExpr" }
            ?: kotlin.error("Cannot locate the TableQuery function fromExpr from the caller type: ${caller.type.dumpKotlinLike()}")

        with(builder) {
          this.irCall(fromExprFunction, expression.type).apply {
            this.dispatchReceiver = caller
            putTypeArgument(0, expression.type)
            putValueArgument(0, liftedEntity)
          }
        }
      }
    ) ?: expression
}
