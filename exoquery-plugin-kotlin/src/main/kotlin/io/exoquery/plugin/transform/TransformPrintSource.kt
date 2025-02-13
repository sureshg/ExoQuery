package io.exoquery.plugin.transform

import io.decomat.Is
import io.decomat.case
import io.decomat.match
import io.decomat.on
import io.exoquery.annotation.DslExt
import io.exoquery.parseError
import io.exoquery.plugin.hasAnnotation
import io.exoquery.plugin.trees.Ir
import io.exoquery.plugin.logging.CompileLogger
import io.exoquery.plugin.logging.Messages
import io.exoquery.plugin.printing.dumpSimple
import io.exoquery.plugin.trees.LocationContext
import io.exoquery.plugin.trees.ParserContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.path
import org.jetbrains.kotlin.ir.expressions.IrBlock
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

class TransformPrintSource(
  override val ctx: BuilderContext,
  val superTransformer: VisitTransformExpressions
): Transformer<IrCall>() {
  private val compileLogger = ctx.logger

  context(BuilderContext, CompileLogger)
  override fun matchesBase(expression: IrCall): Boolean =
    expression.symbol.owner.hasAnnotation<DslExt>()

  sealed interface MatchedType {
    data class Multi(val irs: IrBlockBody): MatchedType
    data class Single(val ir: IrExpression): MatchedType
  }

  context(LocationContext, BuilderContext, CompileLogger)
  override fun transformBase(expression: IrCall): IrExpression =
      with(compileLogger) {
        with (ctx.makeLifter()) {
          on(expression).match(
            case(Ir.Call.FunctionUntethered1[Is("io.exoquery.printSource"), Ir.FunctionExpression.withReturnOnlyBlock[Is()]]).then { _, (ret) ->
              transformPrintSource(MatchedType.Single(ret))
            },
            // printExpr(.. { stuff }: IrFunctionExpression  ..): FunctionCall
            case(Ir.Call.FunctionUntethered1[Is("io.exoquery.printSource"), Ir.FunctionExpression.withBlock[Is(), Is()]]).then { _, (_, args) ->
              transformPrintSource(MatchedType.Multi(args))
            },
            case(Ir.Call.FunctionUntethered0[Is("currentSourceFile")]).then {
              currentFileRaw.path.lift()
            },
            case(Ir.Call.FunctionMem0.WithCaller[Is(), Is("ExoGoldenTest")]).then { caller ->
              caller.call("ExoGoldenTestExpr").invoke(currentFileRaw.path.lift())
            },
            case(Ir.Call.FunctionMem0.WithCaller[Is(), Is("ExoGoldenOverride")]).then { caller ->
              caller.call("ExoGoldenOverrideExpr").invoke(currentFileRaw.path.lift())
            }
          )
        }
      } ?: parseError("Parsing Failed\n================== The expresson was not a Global Function (with one argument-block): ==================\n" + expression.dumpKotlinLike() + "\n--------------------------\n" + expression.dumpSimple())

  context(LocationContext, BuilderContext, CompileLogger)
  fun transformPrintSource(argsRaw: MatchedType) = run {
    val args = when(argsRaw) {
      is MatchedType.Single ->
        argsRaw.copy(superTransformer.visitExpression(argsRaw.ir))
      is MatchedType.Multi ->
        argsRaw.copy(superTransformer.visitBlockBody(argsRaw.irs) as IrBlockBody)
    }

    val printSourceExpr = ctx.pluginCtx
      .referenceFunctions(
        CallableId(FqName("io.exoquery"), Name.identifier("printSourceExpr"))
      ).first()

    val message =
      when(args) {
        is MatchedType.Single -> Messages.PrintingMessageSingle(args.ir, "Single Return")
        is MatchedType.Multi -> Messages.PrintingMessageMulti(args.irs.statements, "Multi Return Statements")
      }

    compileLogger.warn(message)

    with(ctx.builder) {
      this.irCall(printSourceExpr).apply {
        putValueArgument(0, irString(message))
      }
    }
  }

}
