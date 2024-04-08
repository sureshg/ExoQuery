package io.exoquery.printing

import io.exoquery.fansi.Attrs
import io.exoquery.pprint.PPrinter
import io.exoquery.pprint.PPrinterConfig
import io.exoquery.pprint.Tree
import io.exoquery.sql.DistinctKind
import io.exoquery.xr.*

fun exoPrint(value: Any) =
  PrintXR(PrintXR.defaultConfig)(value).toString()


fun <T> iteratorOf(vararg elements: T): Iterator<T> = elements.iterator()


class PrintXR(config: PPrinterConfig = defaultConfig): PPrinter(config) {
  fun treeifySuper(x: Any?) =
    super.treeify(x, escapeUnicode = config.defaultEscapeUnicode, showFieldNames = defaultConfig.defaultShowFieldNames)

  fun treeifyThis(x: Any?) =
    treeify(x, escapeUnicode = config.defaultEscapeUnicode, showFieldNames = defaultConfig.defaultShowFieldNames)

  override fun treeify(x: Any?, escapeUnicode: Boolean, showFieldNames: Boolean): Tree =
    when (x) {
      is XRType ->
        when(x) {
          is XRType.Product -> Tree.Literal("${x.name.takeLastWhile { it != '.' }}(...)")
          else -> Tree.Literal(x.shortString())
        }
      is XR.Product -> Tree.Apply("Product", (listOf(treeifyThis(x.name.takeLastWhile { it != '.' })) + x.fields.map { treeifyThis(it) }).iterator())
      is XR.Infix -> Tree.Apply("Infix", iteratorOf(treeifySuper(x.parts), treeifySuper(x.params)))
      is XR.Ident -> Tree.Apply("Id", iteratorOf(Tree.Literal(x.name), treeifyThis(x.type)))
      is XR.Location.File -> Tree.Literal("<Location:${x.path}:${x.row}:${x.col}>")
      is XR.Location.Synth -> Tree.Literal("<Location:Synthetic>")
      is DistinctKind -> Tree.Literal(x::class.simpleName ?: "BinaryOp?")
      is Operator -> Tree.Literal(x.symbol)
      is XR ->
        when (val tree = super.treeify(x, escapeUnicode, showFieldNames)) {
          is Tree.Apply -> {
            val superNodes = tree.body.asSequence()
              .filterNot { (it is Tree.KeyValue && it.key == "loc") }
              .filterNot { (it is Tree.Apply && it.prefix == "Location") }
              .filterNot { (it is Tree.Literal && it.body.startsWith("<Location:")) }.toList()
            Tree.Apply(tree.prefix, superNodes.iterator())
          }
          else -> tree
        }
      else -> super.treeify(x, escapeUnicode, showFieldNames)
    }

  companion object {
    val defaultConfig = PPrinterConfig(defaultWidth = 200, defaultShowFieldNames = false)
    val BlackWhite =
      PrintXR(PPrinterConfig().copy(
        colorLiteral = Attrs.Empty,
        colorApplyPrefix = Attrs.Empty
      ))
  }
}