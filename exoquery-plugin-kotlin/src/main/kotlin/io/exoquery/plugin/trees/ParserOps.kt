package io.exoquery.plugin.trees

import io.exoquery.CapturedBlock
import io.exoquery.plugin.isClass
import io.exoquery.plugin.safeName
import io.exoquery.plugin.transform.CX
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.declarations.nameWithPackage
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.ir.util.kotlinFqName


context(CX.Symbology)
fun IrGetValue.isCapturedFunctionArgument(): Boolean = run {
  val gv = this
  symbolSet.capturedFunctionParameters.find { gv.symbol.owner == it } != null
}

context(CX.Scope)
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

context(CX.Scope)
fun IrGetValue.showLineage(): String {
  val collect = mutableListOf<String>()
  fun IrVariable.declSymbol(): String = if (this.isVar) "var" else "val"

  tailrec fun rec(elem: IrElement, recurseCount: Int): Unit {
    val prefix =
      "${(elem as? IrFunction)?.let { "fun " + it.symbol.safeName +"(...)" } 
        ?: (elem as? IrVariable)?.let { "${it.declSymbol()} " + it.symbol.safeName } 
        ?: (elem as? IrValueParameter)?.let { "param " + it.symbol.safeName }
        ?: (elem as? IrFile)?.let { "File(${it.nameWithPackage})" }  
        ?: (elem::class.simpleName ?: "Unknown")}"

    when {
      recurseCount == 0 -> {
        collect.add("${prefix}->RECURSION LIMIT HIT")
        Unit
      }
      elem is IrFunction && elem.extensionReceiverParameter?.type?.isClass<CapturedBlock>() ?: false -> {
        collect.add("${prefix}->${elem.symbol.safeName}-in CapturedBlock")
        Unit
      }
      elem is IrFunction -> {
        collect.add("${prefix}->fun.Owner")
        rec(elem.symbol.owner.parent, recurseCount-1)
      }
      elem is IrValueParameter -> {
        collect.add("${prefix}->param.Owner")
        rec(elem.symbol.owner.parent, recurseCount-1)
      }
      elem is IrVariable -> {
        collect.add("${prefix}->${elem.declSymbol()}.Owner")
        rec(elem.symbol.owner.parent, recurseCount-1)
      }
      else ->
        collect.add(prefix)
    }
  }

  rec(this.symbol.owner, 100)
  return collect.map { "[${it}]" }.joinToString("->")
}
