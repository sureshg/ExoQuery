package io.exoquery.plugin.trees

import io.decomat.Is
import io.decomat.case
import io.decomat.on
import io.exoquery.ParseError
import io.exoquery.plugin.logging.CompileLogger
import io.exoquery.parseError
import io.exoquery.plugin.location
import io.exoquery.plugin.locationXR
import io.exoquery.plugin.logging.Location
import io.exoquery.plugin.show
import io.exoquery.xr.XR
import io.exoquery.xr.XRType
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.isBoolean
import org.jetbrains.kotlin.ir.util.dumpKotlinLike

object TypeParser {
  context(ParserContext, CompileLogger) fun of(expr: IrExpression) =
    ofElementWithType(expr, expr.type)

  context(ParserContext, CompileLogger) fun of(expr: IrVariable) =
    ofElementWithType(expr, expr.type)

  context(ParserContext, CompileLogger) fun of(expr: IrFunction) =
    ofElementWithType(expr, expr.returnType)

  context(ParserContext, CompileLogger) fun of(expr: IrValueParameter) =
    ofElementWithType(expr, expr.type)

  context(ParserContext, CompileLogger) fun ofTypeAt(type: IrType, loc: Location): XRType =
    try {
      parse(type)
    } catch (e: ParseError) {
      parseError("""(${loc.show()}) ERROR Could not parse type: ${type.dumpKotlinLike()}""")
    }

  context(ParserContext, CompileLogger) private fun ofElementWithType(expr: IrElement, type: IrType) =
    try {
      parse(type)
    } catch (e: ParseError) {
      val loc = expr.location()
      parseError(
        """|(${loc.show()}) ERROR Could not parse type: ${type.dumpKotlinLike()} 
           |====== from the statement ======
           |${expr.dumpKotlinLike()}
           |""".trimMargin())
    }

  context(ParserContext, CompileLogger) private fun parse(expr: IrType): XRType =
    on(expr).match<XRType>(
      // TODO why for Components1 it's (type) bot for Components2 it's (type, type)
      //     think this is a bug with DecoMat.
      case(Ir.Type.SqlVariable[Is()]).then { realType ->
        parse(realType)
      },

      // For now treat lists like value types, may way to change in future
      case(Ir.Type.KotlinList[Is()]).then { realType ->
        XRType.Value
      },

      case(Ir.Type.Query[Is()]).then { realType ->
        parse(realType)
      },

      case(Ir.Type.DataClass[Is(), Is()]).then { name, props ->
        val fieldTypeXRs = props.map { (fieldName, fieldType) -> fieldName to parse(fieldType) }
        warn("------------- Parsed Class props of: ${name}: ${fieldTypeXRs.map { (a, b) -> "$a -> $b" }} -------------------")
        XRType.Product(name, fieldTypeXRs)
      },

      case(Ir.Type.Value[Is()]).then { type ->
        //error("----------- Got here: ${type} ----------")
        if (type.isBoolean())
          XRType.BooleanValue
        else
          XRType.Value
      },

      case(Ir.Type.Generic[Is()]).then { type ->
        //error("----------- Got here: ${type} ----------")
        XRType.Generic
      }
    ) ?: run {
      val loc = currentLocation()
      parseError(
        """|(${loc.path}:${loc.line}:${loc.column}) ERROR Could not parse type from: ${expr.dumpKotlinLike()}
           |""".trimMargin())
    }

}