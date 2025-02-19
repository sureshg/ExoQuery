package io.exoquery

import io.exoquery.plugin.location
import io.exoquery.plugin.printing.Messages
import io.exoquery.plugin.printing.dumpSimple
import io.exoquery.plugin.source
import io.exoquery.plugin.symName
import io.exoquery.plugin.transform.BuilderContext
import io.exoquery.plugin.transform.LocateableContext
import io.exoquery.plugin.trees.LocationContext
import io.exoquery.plugin.trees.ParserContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.util.dumpKotlinLike

class LiftingError(val msg: String): Exception(msg)
fun liftingError(msg: String): Nothing = throw LiftingError(msg)

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

fun parseErrorFromType(msg: String, location: CompilerMessageSourceLocation): Nothing = throw ParseError(io.exoquery.plugin.logging.Messages.TypeParseErrorMsg(msg), location)

context(LocateableContext)
fun parseErrorFromType(msg: String, expr: IrElement): Nothing = throw throw ParseError.withFullMsg(io.exoquery.plugin.logging.Messages.TypeParseErrorMsg(msg), expr, currentFile, expr.location())

context(LocateableContext) fun parseError(msg: String, expr: IrElement): Nothing = throw ParseError.withFullMsg(msg, expr, currentFile, expr.location())
context(BuilderContext) fun builderParseError(msg: String, expr: IrElement): Nothing = throw ParseError.withFullMsg(msg, expr, currentFile, expr.location(currentFile.fileEntry))

context(LocateableContext) fun parseErrorSym(expr: IrCall): Nothing =
  throw ParseError.withFullMsg("Invalid function name or symbol: ${expr.symName}", expr, currentFile, expr.location())
