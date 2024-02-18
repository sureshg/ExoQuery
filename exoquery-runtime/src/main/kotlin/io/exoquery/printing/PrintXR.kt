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
      is XRType -> Tree.Literal(x.shortString())
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