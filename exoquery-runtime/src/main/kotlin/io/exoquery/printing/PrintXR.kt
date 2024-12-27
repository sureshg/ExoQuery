package io.exoquery.printing

import io.decomat.*
import io.exoquery.fansi.Attrs
import io.exoquery.kmp.pprint.PPrinter
import io.exoquery.pprint.PPrinterConfig
import io.exoquery.pprint.Tree
import io.exoquery.xr.*
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.serializer
import io.decomat.HasProductClass as PC

fun <T> iteratorOf(vararg elements: T): Iterator<T> = elements.iterator()

object PrintXRType {
  val BlackWhite = PrintXR(XRType.serializer(), PrintXR.defaultConfig)
}

fun qprint(xr: XR) = PrintXR.Color.invoke(xr)

class PrintXR<T>(serializer: SerializationStrategy<T>, config: PPrinterConfig = defaultConfig): PPrinter<T>(serializer, config) {
  fun treeifySuper(x: T) =
    super.treeify(x, escapeUnicode = config.defaultEscapeUnicode, showFieldNames = defaultConfig.defaultShowFieldNames)

  fun treeifyThis(x: T) =
    treeify(x, escapeUnicode = config.defaultEscapeUnicode, showFieldNames = defaultConfig.defaultShowFieldNames)

  override fun treeify(x: T, escapeUnicode: Boolean, showFieldNames: Boolean): Tree =
    when (x) {
      is XRType ->
        when(x) {
          is XRType.Product -> Tree.Literal("${x.name.takeLastWhile { it != '.' }}(...)")
          else -> Tree.Literal(x.shortString())
        }
      //is XR.Product -> Tree.Apply("Product", (listOf(treeifyThis(x.name.takeLastWhile { it != '.' })) + x.fields.map { treeifyThis(it) }).iterator())
      //is XR.Infix -> Tree.Apply("Infix", iteratorOf(treeifySuper(x.parts), treeifySuper(x.params)))
      is XR.Ident -> Tree.Apply("Id", iteratorOf(Tree.Literal(x.name), PrintXR(XRType.serializer(), config).treeifyThis(x.type)))
      is XR.Location.File -> Tree.Literal("<Location:${x.path}:${x.row}:${x.col}>")
      is XR.Location.Synth -> Tree.Literal("<Location:Synthetic>")
      //is DistinctKind -> Tree.Literal(x::class.simpleName ?: "BinaryOp?")
      is Operator -> Tree.Literal(x.symbol)
      //is PC<*> -> Tree.Apply(x::class.simpleName ?: "PC?", run {
      //  when (val pc = x.productComponents) {
      //    is ProductClass0 -> iteratorOf()
      //    is ProductClass1<*, *> -> iteratorOf(treeifyThis(pc.a))
      //    is ProductClass2<*, *, *> -> iteratorOf(treeifyThis(pc.a), treeifyThis(pc.b))
      //    is ProductClass2M<*, *, *, *> -> iteratorOf(treeifyThis(pc.a), treeifyThis(pc.m), treeifyThis(pc.b))
      //    else -> iteratorOf()
      //  }
      //})
      is XR ->
        when (val tree = super.treeify(x, escapeUnicode, showFieldNames)) {
          is Tree.Apply -> {
            val superNodes = tree.body.asSequence()
              .filterNot { (it is Tree.KeyValue && it.key == "loc") }
              .filterNot { (it is Tree.Apply && it.prefix == "Location") }
              .filterNot { (it is Tree.Apply && it.prefix == "File") }
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
      PrintXR(XR.serializer(), PPrinterConfig().copy(
        defaultShowFieldNames = false,
        colorLiteral = Attrs.Empty,
        colorApplyPrefix = Attrs.Empty
      ))
    val Color =
      PrintXR(XR.serializer(), PPrinterConfig().copy(
        defaultShowFieldNames = false
      ))
  }
}