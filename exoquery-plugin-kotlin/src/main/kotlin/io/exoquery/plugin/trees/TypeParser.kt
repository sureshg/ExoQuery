package io.exoquery.plugin.trees

import io.decomat.Is
import io.decomat.case
import io.decomat.on
import io.exoquery.plugin.logging.CompileLogger
import io.exoquery.parseError
import io.exoquery.xr.XRType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.isBoolean
import org.jetbrains.kotlin.ir.util.dumpKotlinLike

object TypeParser {
  context(ParserContext, CompileLogger) fun parse(expr: IrType): XRType =
    on(expr).match<XRType>(
      // TODO why for Components1 it's (type) bot for Components2 it's (type, type)
      //     think this is a bug with DecoMat.
      case(Ir.Type.SqlVariable[Is()]).then { realType ->
        parse(realType)
      },

      case(Ir.Type.Query[Is()]).then { realType ->
        parse(realType)
      },

      case(Ir.Type.DataClass[Is(), Is()]).then { name, props ->
        val fieldTypeXRs = props.map { (fieldName, fieldType) -> fieldName to parse(fieldType) }
        XRType.Product(name, fieldTypeXRs)
      },

      // TODO Don't understand why this doesn't work!!!
      case(Ir.Type.Value[Is()]).then { type ->
        //error("----------- Got here: ${type} ----------")
        if (type.isBoolean())
          XRType.BooleanValue
        else
          XRType.Value
      }

    ) ?: parseError("Could not parse type from: ${expr.dumpKotlinLike()}")

}