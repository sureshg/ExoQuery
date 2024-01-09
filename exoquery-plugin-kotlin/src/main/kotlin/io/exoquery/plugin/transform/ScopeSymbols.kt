package io.exoquery.plugin.transform

import org.jetbrains.kotlin.ir.symbols.IrSymbol

data class ScopeSymbols(val symbols: List<IrSymbol>) {
  operator fun plus(other: ScopeSymbols) = ScopeSymbols(this.symbols + other.symbols)
  operator fun contains(sym: IrSymbol) = symbols.contains(sym)

  companion object {
    val empty = ScopeSymbols(listOf())
  }
}