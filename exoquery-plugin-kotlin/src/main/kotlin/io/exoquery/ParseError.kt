package io.exoquery

import io.exoquery.plugin.location
import io.exoquery.plugin.printing.dumpSimple
import io.exoquery.plugin.source
import io.exoquery.plugin.transform.LocateableContext
import io.exoquery.plugin.trees.LocationContext
import io.exoquery.plugin.trees.ParserContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.util.dumpKotlinLike

class ParseError(val msg: String, val location: CompilerMessageSourceLocation?) : Exception(msg) {
  companion object {
    fun withFullMsg(msg: String, element: IrElement, file: IrFile, location: CompilerMessageSourceLocation): ParseError {
      val fullMsg: String = with (LocateableContext.makeLite(file)) {
        val expressionPart =
          element.source()?.let { src ->
            """|
               |------------ Source ------------
               |${src}""".trimMargin()
          }

        """|[ExoQuery] Count not understand an expression or query due to an error: ${msg}.${expressionPart}
           |------------ Raw Expression ------------
           |${element.dumpKotlinLike()}
           |------------ Raw Expression Tree ------------
           |${element.dumpSimple()}
           |""".trimMargin()
      }
      return ParseError(fullMsg, location)
    }
  }
}

fun parseError(msg: String, location: CompilerMessageSourceLocation? = null): Nothing = throw ParseError(msg, location)

context(LocateableContext) fun parseError(msg: String, expr: IrElement): Nothing = throw ParseError.withFullMsg(msg, expr, currentFile, expr.location())
