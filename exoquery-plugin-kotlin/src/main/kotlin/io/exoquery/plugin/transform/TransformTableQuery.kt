package io.exoquery.plugin.transform

import io.decomat.Is
import io.decomat.case
import io.decomat.on
import io.exoquery.EntityExpression
import io.exoquery.Table
import io.exoquery.plugin.classIdOf
import io.exoquery.plugin.locationXR
import io.exoquery.xr.XR
import io.exoquery.plugin.logging.CompileLogger
import io.exoquery.plugin.printing.DomainErrors
import io.exoquery.plugin.safeName
import io.exoquery.plugin.trees.*
import io.exoquery.terpal.parseError
import io.exoquery.xr.XRType
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

class TransformTableQuery(override val ctx: BuilderContext): Transformer() {
  private val compileLogger = ctx.logger

  context(BuilderContext, CompileLogger)
  override fun matchesBase(expression: IrCall): Boolean = run {
    with(compileLogger) {
      on(expression).match(
        case(ExtractorsDomain.Call.MakeTable[Is()]).then { true }
      ) ?: false
    }
  }

  fun IrType.classOrFail(msg: String): IrClassSymbol {
    val cls = this.classOrNull
    return when {
      cls == null -> kotlin.error("$msg. Could not get the a class symbol from the type: ${this.dumpKotlinLike()}")
      else -> cls
    }
  }

  fun XRType.productOrFail(originalType: IrType): XRType.Product =
    when(this) {
      is XRType.Product -> this
      else -> DomainErrors.NotProductTypeParsedFromType(this, originalType)

    }

  context(ParserContext, BuilderContext, CompileLogger)
  override fun transformBase(expression: IrCall): IrExpression =
    on(expression).match(
      case(ExtractorsDomain.Call.MakeTable[Is()]).thenThis { tableData ->
        val lifter = ctx.makeLifter()
        val entityClass = tableData.tableType
        val xrType = TypeParser.of(this).productOrFail(entityClass)
        val xr = XR.Entity(entityClass.classOrFail("Error derving class of TableQuery").safeName, xrType, expression.locationXR())

        val tableCompanionRef = pluginCtx.referenceClass(classIdOf<Table.Companion>()) ?: parseError("Cannot find the table-constructor class ${classIdOf<Table.Companion>()}. This should be impossible.")
        val caller = builder.irGetObject(tableCompanionRef)

        val entityExpression = EntityExpression(xr)
        val liftedEntity = lifter.liftExpression(entityExpression)

        Caller.Dispatch(caller).callWithParams(tableData.replacementMethodToCall, listOf(expression.type)).invoke(liftedEntity)
      }
    ) ?: expression
}
