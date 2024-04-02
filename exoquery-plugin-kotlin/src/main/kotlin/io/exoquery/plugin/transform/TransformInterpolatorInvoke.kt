package io.exoquery.plugin.transform

import io.exoquery.terpal.UnzipPartsParams
import io.exoquery.terpal.parseError
import io.decomat.*
import io.exoquery.SQL
import io.exoquery.plugin.isClass
import io.exoquery.plugin.location
import io.exoquery.plugin.locationXR
import io.exoquery.plugin.logging.CompileLogger
import io.exoquery.plugin.printing.dumpSimple
import io.exoquery.plugin.trees.*
import io.exoquery.plugin.trees.ExtractorsInterpolate.Call
import org.jetbrains.kotlin.ir.backend.js.utils.typeArguments
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

class TransformInterepolatorInvoke(override val ctx: BuilderContext, val superTransformer: VisitTransformExpressions): Transformer() {
  private val compileLogger = ctx.logger

  context(BuilderContext, CompileLogger)
  override fun matchesBase(expression: IrCall): Boolean =
    Call.InterpolateInvoke.matchesMethod(expression)

  // TODO need to parse the XR to get the params
  context(ParserContext, BuilderContext, CompileLogger)
  override fun transformBase(expression: IrCall): IrExpression {
    val (caller, compsRaw) =
      with(compileLogger) {
        on(expression).match(
          // interpolatorSubclass.invoke(.. { stuff } ...)
          case(Call.InterpolateInvoke[Is(), Is()]).then { caller, comps ->
            caller to comps
          }
        )
      } ?: run {
        val bar = "\${bar}"
        parseError(
          """|======= Parsing Error =======
           |The contents of Interpolator.invoke(...) must be a single String concatenation statement e.g:
           |myInterpolator.invoke("foo $bar baz")
           |
           |==== However, the following was found: ====
           |${expression.dumpKotlinLike()}
           |======= IR: =======
           |${expression.dumpSimple()}"
        """.trimMargin()
        )
      }



    // before doing anything else need to run recursive transformations on the components because they could be
    // there could be nested interpolations e.g. stmt("foo_#{stmt("bar")}_baz")
    val comps = compsRaw.map { it.transform(superTransformer, internalVars) }

    val concatStringExprs =
      { a: IrExpression, b: IrExpression ->
        with (ctx) {
          ctx.builder.irString("").callDispatch("plus")(a).callDispatch("plus")(b)
        }
      }

    val (parts, paramsRaw) =
      UnzipPartsParams<IrExpression>({ it.isClass<String>() }, concatStringExprs, { ctx.builder.irString("") })
        .invoke(comps)

    val paramsAndBinds =
      paramsRaw.withIndex().map { (idx, param) ->
        Parser.parseExpression(param)
      }

    //error("----------- Original Binds List ----------\n" + paramsAndBinds.map { it.second.show() })

    val binds = paramsAndBinds.map { it.second }.fold(DynamicBindsAccum.empty()) { a, b -> a + b }
    val params = paramsAndBinds.map { it.first }

    warn("----------- Ouptut Binds List ----------\n" + binds.show())

    return with(ctx) {
      val lifter = makeLifter()
      val partsLifted = with (lifter) { parts.liftExprTyped(context.symbols.string.defaultType) }
      val paramsLifted = with(lifter) { params.map { liftXR(it) }.lift { it } }
      val locLifted = lifter.liftLocation(expression.locationXR())
      val bindsLifted = binds.makeDynamicBindsIr()

      val typeArg = expression.typeArguments[0] ?: parseError("no type argument found on SQL.invoke<T>(...). This should be impossible.")
      val xrType = TypeParser.ofTypeAt(typeArg, expression.location())
      val typeLifted = lifter.liftXRType(xrType)

      val callOutput =
        caller.callDispatchWithParamsAndOutput("interpolate", listOf(typeArg), expression.type)(
          partsLifted,
          paramsLifted,
          typeLifted,
          bindsLifted,
          locLifted
        )

      callOutput
    }
  }
}
