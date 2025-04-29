package io.exoquery.plugin.printing

import io.exoquery.fansi.Attrs
import io.exoquery.pprint.PPrinter
import io.exoquery.pprint.PPrinterConfig
import io.exoquery.pprint.Tree
import io.exoquery.xr.XR
import io.exoquery.xr.XRType
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.util.dumpKotlinLike

class PrintCompiletimes(config: PPrinterConfig = defaultConfig) : PPrinter(config) {
  override fun treeify(x: Any?, elementName: String?, escapeUnicode: Boolean, showFieldNames: Boolean): Tree =
    when (x) {
      is IrExpression ->
        Tree.Literal(x.dumpKotlinLike(), elementName)
      is XRType ->
        when (x) {
          is XRType.Product -> Tree.Literal("${x.name}(...)", elementName)
          else -> Tree.Literal(x.shortString(), elementName)
        }
      is XR.Location.File -> Tree.Literal("<Location:${x.path}:${x.row}:${x.col}>", elementName)
      is XR.Location.Synth -> Tree.Literal("<Location:Synthetic>", elementName)
      is XR ->
        when (val tree = super.treeify(x, elementName, escapeUnicode, showFieldNames)) {
          is Tree.Apply -> {
            val superNodes = tree.body.asSequence().filterNot { (it is Tree.Literal && it.body.startsWith("<Location:")) }.toList()
            Tree.Apply(tree.prefix, superNodes.iterator(), elementName)
          }
          else -> tree
        }
      else -> super.treeify(x, elementName, escapeUnicode, showFieldNames)
    }

  companion object {
    val defaultConfig = PPrinterConfig(defaultWidth = 200, defaultShowFieldNames = false)
    val BlackWhite =
      PrintCompiletimes(
        PPrinterConfig().copy(
          colorLiteral = Attrs.Empty,
          colorApplyPrefix = Attrs.Empty
        )
      )
  }
}
