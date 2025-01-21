package io.exoquery

import io.exoquery.plugin.location
import io.exoquery.plugin.trees.ParserContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.expressions.IrExpression

class ParseError(val msg: String, val location: CompilerMessageSourceLocation?) : Exception(msg)

fun parseError(msg: String, location: CompilerMessageSourceLocation? = null): Nothing = throw ParseError(msg, location)
context(ParserContext) fun parseError(msg: String, expr: IrElement): Nothing = throw ParseError(msg, expr.location())
