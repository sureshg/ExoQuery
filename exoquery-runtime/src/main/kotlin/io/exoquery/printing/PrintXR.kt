package io.exoquery.printing

import io.exoquery.fansi.Attrs
import io.exoquery.pprint.PPrinter
import io.exoquery.pprint.PPrinterConfig
import io.exoquery.pprint.Tree
import io.exoquery.xr.MirrorIdiom
import io.exoquery.xr.XR
import io.exoquery.xr.XRType

//fun printxr(xr: XR) {
//  val m = MirrorIdiom
//}

class PrintXR(config: PPrinterConfig = defaultConfig): PPrinter(config) {
  override fun treeify(x: Any?, escapeUnicode: Boolean, showFieldNames: Boolean): Tree =
    when (x) {
      is XRType ->
        when(x) {
          is XRType.Product -> Tree.Literal("${x.name}(...)")
          else -> Tree.Literal(x.shortString())
        }
      is XR.Location.File -> Tree.Literal("<Location:${x.path}:${x.row}:${x.col}>")
      is XR.Location.Synth -> Tree.Literal("<Location:Synthetic>")
      is XR ->
        when (val tree = super.treeify(x, escapeUnicode, showFieldNames)) {
          is Tree.Apply -> {
            val superNodes = tree.body.asSequence().filterNot { (it is Tree.Literal && it.body.startsWith("<Location:")) }.toList()
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