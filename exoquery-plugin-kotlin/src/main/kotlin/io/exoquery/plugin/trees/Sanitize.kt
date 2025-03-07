package io.exoquery.plugin.trees

import io.exoquery.plugin.safeName
import io.exoquery.xr.XR
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

fun FqName.sanitizedClassName(): String =
  this.pathSegments().filterNot { it.toString() == "<init>" || it.toString() == "<anonymous>" }.last().asString()

private val dol: Char = '$'

fun String.sanitizeIdentName() =
  if (this.startsWith("${dol}this"))
    this
  else
    this.replace("<", "").replace(">", "")

val IrValueParameter.unadulteratedName:String get() = this.name.asString()

fun IrSymbol.sanitizedSymbolName(): String =
  ((this.owner as? IrValueParameter)?.let { it.unadulteratedName } ?: this.safeName).sanitizeIdentName()

fun IrValueParameter.sanitizedSymbolName() = this.unadulteratedName.sanitizeIdentName()

fun Name.sanitizedSymbolName() = this.asString().sanitizeIdentName()
