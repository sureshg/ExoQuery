package io.exoquery.plugin.transform

import io.exoquery.plugin.logging.Location
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.symbols.IrValueSymbol

data class PrintableQuery(val query: String, val location: Location, val label: String? = null) {
  override fun toString(): String = query
}

sealed interface FileQueryAccum {
  fun isEmpty(): Boolean
  fun nonEmpty(): Boolean = !isEmpty()

  data object Empty: FileQueryAccum {
    override fun isEmpty(): Boolean = true
  }
  data class RealFile(val file: IrFile, val queries: MutableList<PrintableQuery> = mutableListOf()): FileQueryAccum {
    override fun isEmpty(): Boolean = queries.isEmpty()

    fun addQuery(printableQuery: PrintableQuery) {
      queries.add(printableQuery)
    }
  }

  sealed interface PathBehavior {
    data object IncludePaths: PathBehavior
    data object NoIncludePaths: PathBehavior
  }
  sealed interface LabelBehavior {
    data object IncludeOnlyLabeled: LabelBehavior
    data object IncludeAll: LabelBehavior
  }
}



// This is the context that the query transformer passes around. I would have liked it to be completely immutable but
// we need to be able to accumulate queries that are created in the TransformCompileQuery instances therefore
// we have a mutable list of queries here.
data class TransformerScope(
  val symbols: List<IrSymbol>,
  val capturedFunctionParameters: List<IrValueParameter> = emptyList(),
  private val fileQueryAccum: FileQueryAccum) {

  fun withSymbols(newSymbols: List<IrSymbol>) = TransformerScope(this.symbols + newSymbols, capturedFunctionParameters, fileQueryAccum)
  // Add parameters for captured functions (don't think there are cases where this is called in a nested fasion i.e. there are symbols that should exist beforehand)
  fun withCapturedFunctionParameters(newCapturedFunctionParameters: List<IrValueParameter>) = TransformerScope(symbols, this.capturedFunctionParameters + newCapturedFunctionParameters, fileQueryAccum)

  fun hasQueries(): Boolean =
    when (val fileQueryAccum = this.fileQueryAccum) {
      is FileQueryAccum.Empty ->
        false
      is FileQueryAccum.RealFile ->
        fileQueryAccum.queries.isNotEmpty()
    }

  fun currentQueries(): List<PrintableQuery> =
    when (val fileQueryAccum = this.fileQueryAccum) {
      is FileQueryAccum.Empty ->
        emptyList()
      is FileQueryAccum.RealFile ->
        fileQueryAccum.queries
    }

  // TODO add optional arguemnt to take a currentFile path to use if none-exists already?
  fun addQuery(printableQuery: PrintableQuery) {
    when (val fileQueryAccum = this.fileQueryAccum) {
      is FileQueryAccum.Empty ->
        error("------- Cannot add query to empty file: -------\n${printableQuery.query}")
      is FileQueryAccum.RealFile ->
        fileQueryAccum.addQuery(printableQuery)
    }
  }
}
