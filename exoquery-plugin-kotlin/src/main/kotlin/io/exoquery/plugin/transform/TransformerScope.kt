package io.exoquery.plugin.transform

import io.exoquery.plugin.logging.Location
import org.jetbrains.kotlin.ir.symbols.IrSymbol

data class PrintableQuery(val query: String, val location: Location)

class FileQueryAccum(val queries: MutableList<PrintableQuery> = mutableListOf()) {
  fun addQuery(query: String, location: Location) {
    queries.add(PrintableQuery(query, location))
  }
}

// This is the context that the query transformer passes around. I would have liked it to be completely immutable but
// we need to be able to accumulate queries that are created in the TransformCompileQuery instances therefore
// we have a mutable list of queries here.
data class TransformerScope(val symbols: List<IrSymbol>, val fileQueryAccum: FileQueryAccum) {
  fun withSymbols(newSymbols: List<IrSymbol>) = TransformerScope(this.symbols + newSymbols, fileQueryAccum)
  fun containsSymbol(sym: IrSymbol) = symbols.contains(sym)

  //companion object {
  //  val empty = ScopeSymbols(listOf())
  //}
}