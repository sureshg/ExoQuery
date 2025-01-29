package io.exoquery.plugin.transform

import io.exoquery.plugin.logging.Location
import io.exoquery.plugin.transform.FileQueryAccum.LabelBehavior
import io.exoquery.plugin.transform.FileQueryAccum.PathBehavior
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.symbols.IrSymbol

object QueryFileTextMaker {
  val LabelPrefix: String = "-- ===========< "
  val LabelSuffix: String = " >========== --"
  val PathPrefix: String = "-- Path: "

  operator fun invoke(queries: List<PrintableQuery>, pathBehavior: PathBehavior, labelBehavior: LabelBehavior): String =
    queries
      .mapNotNull { query ->
        when (labelBehavior) {
          is LabelBehavior.IncludeOnlyLabeled -> if (query.label != null) query else null
          is LabelBehavior.IncludeAll -> query
        }
      }
      .mapNotNull { query ->
        val labelLine = query.label?.let { "${LabelPrefix}$it${LabelSuffix}" } ?: ""
        val pathLine = "${PathPrefix}${query.location.path}"
        when (pathBehavior) {
          is PathBehavior.IncludePaths ->
            // first the label prefix, label, label suffix, then the path, then the query
            """|
               |$labelLine
               |$pathLine
               |${query.query}
               |
            """.trimMargin()
          is PathBehavior.NoIncludePaths ->
            // first the label prefix, label, label suffix, then the query
            """|
               |$labelLine
               |${query.query}
               |
            """.trimMargin()
        }
      }.joinToString("\n")
}

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
data class TransformerScope(val symbols: List<IrSymbol>, private val fileQueryAccum: FileQueryAccum) {
  fun withSymbols(newSymbols: List<IrSymbol>) = TransformerScope(this.symbols + newSymbols, fileQueryAccum)
  fun containsSymbol(sym: IrSymbol) = symbols.contains(sym)

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