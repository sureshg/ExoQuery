package io.exoquery.plugin.trees

import io.exoquery.CapturedBlock
import io.exoquery.plugin.classIdOf
import io.exoquery.plugin.isClass
import io.exoquery.plugin.isClassStrict
import io.exoquery.plugin.safeName
import io.exoquery.plugin.transform.CX
import io.exoquery.plugin.transform.containsBatchParam
import io.exoquery.serial.ParamSerializer
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.declarations.nameWithPackage
import org.jetbrains.kotlin.ir.expressions.IrDeclarationReference
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrValueAccessExpression
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.isBoolean
import org.jetbrains.kotlin.ir.types.isChar
import org.jetbrains.kotlin.ir.types.isDouble
import org.jetbrains.kotlin.ir.types.isFloat
import org.jetbrains.kotlin.ir.types.isInt
import org.jetbrains.kotlin.ir.types.isLong
import org.jetbrains.kotlin.ir.types.isShort
import org.jetbrains.kotlin.ir.types.isString
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.name.ClassId


context(CX.Symbology)
fun IrDeclarationReference.isCapturedFunctionArgument(): Boolean = run {
  val gv = this
  symbolSet.capturedFunctionParameters.find { gv.symbol.owner == it } != null
}

// To be used with IrGetValue and IrGetField
context(CX.Scope)
fun IrDeclarationReference.isCapturedVariable(): Boolean {
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

context(CX.Scope, CX.Symbology)
fun IrDeclarationReference.isExternal(): Boolean =
  !isCapturedFunctionArgument() && !isCapturedVariable()

context(CX.Scope, CX.Symbology)
fun IrDeclarationReference.isInternal(): Boolean = !isExternal()

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

fun getSerializerForType(type: IrType): ClassId? =
  when {
    type.isString() -> classIdOf<ParamSerializer.String>()
    type.isChar() -> classIdOf<ParamSerializer.Char>()
    type.isInt() -> classIdOf<ParamSerializer.Int>()
    type.isShort() -> classIdOf<ParamSerializer.Short>()
    type.isLong() -> classIdOf<ParamSerializer.Long>()
    type.isFloat() -> classIdOf<ParamSerializer.Float>()
    type.isDouble() -> classIdOf<ParamSerializer.Double>()
    type.isBoolean() -> classIdOf<ParamSerializer.Boolean>()
    type.isClassStrict<LocalDate>() -> classIdOf<LocalDate>()
    type.isClassStrict<LocalTime>() -> classIdOf<LocalTime>()
    type.isClassStrict<LocalDateTime>() -> classIdOf<LocalDateTime>()
    else -> null
  }
