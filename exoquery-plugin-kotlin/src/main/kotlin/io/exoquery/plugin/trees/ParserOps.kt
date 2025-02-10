package io.exoquery.plugin.trees

import io.exoquery.CapturedBlock
import io.exoquery.plugin.isClass
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrGetValue


context(ParserContext)
fun IrGetValue.isCapturedFunctionArgument(): Boolean = run {
  val gv = this
  capturedFunctionSymbols.find { gv.symbol.owner == it } != null
}

fun IrGetValue.isCapturedVariable(): Boolean {
  tailrec fun rec(elem: IrElement, recurseCount: Int): Boolean =
    when {
      recurseCount == 0 -> false
      elem is IrFunction && elem.extensionReceiverParameter?.type?.isClass<CapturedBlock>() ?: false -> true
      elem is IrFunction -> rec(elem.symbol.owner.parent, recurseCount-1)
      elem is IrValueParameter -> rec(elem.symbol.owner.parent, recurseCount-1)
      elem is IrVariable -> rec(elem.symbol.owner.parent, recurseCount-1)
      else -> false
    }

  return rec(this.symbol.owner, 100)
}
