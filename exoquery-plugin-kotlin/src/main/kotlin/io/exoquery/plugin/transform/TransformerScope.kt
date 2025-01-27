package io.exoquery.plugin.transform

import io.exoquery.plugin.logging.Location
import io.exoquery.plugin.show
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.symbols.IrSymbol

data class PrintableQuery(val query: String, val location: Location)

sealed interface FileQueryAccum {
  fun isEmpty(): Boolean
  fun nonEmpty(): Boolean = !isEmpty()

  data object Empty: FileQueryAccum {
    override fun isEmpty(): Boolean = true
  }
  data class RealFile(val file: IrFile, val queries: MutableList<PrintableQuery> = mutableListOf()): FileQueryAccum {
    override fun isEmpty(): Boolean = queries.isEmpty()

    fun addQuery(query: String, location: Location) {
      queries.add(PrintableQuery(query, location))
    }
  }

  fun makeFileDump(): String =
    when (this) {
      is FileQueryAccum.Empty -> "-- EmptyFile --"
      is FileQueryAccum.RealFile -> {
        val queries =
          queries.map {
           """|
              |-- =========== ${it.location.show()} 
              |${it.query}
              |
            """.trimMargin()
          }.joinToString("\n")
        queries
      }
    }
}



// This is the context that the query transformer passes around. I would have liked it to be completely immutable but
// we need to be able to accumulate queries that are created in the TransformCompileQuery instances therefore
// we have a mutable list of queries here.
data class TransformerScope(val symbols: List<IrSymbol>, val fileQueryAccum: FileQueryAccum) {
  fun withSymbols(newSymbols: List<IrSymbol>) = TransformerScope(this.symbols + newSymbols, fileQueryAccum)
  fun containsSymbol(sym: IrSymbol) = symbols.contains(sym)

  // TODO add optional arguemnt to take a currentFile path to use if none-exists already?
  fun addQuery(query: String, location: Location) {
    when (val fileQueryAccum = this.fileQueryAccum) {
      is FileQueryAccum.Empty ->
        error("------- Cannot add query to empty file: -------\n$query")
      is FileQueryAccum.RealFile ->
        fileQueryAccum.addQuery(query, location)
    }
  }
}