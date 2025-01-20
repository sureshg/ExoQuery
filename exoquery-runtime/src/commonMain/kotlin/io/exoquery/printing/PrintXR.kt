package io.exoquery.printing

import io.exoquery.Params
import io.exoquery.Runtimes
import io.exoquery.SqlExpression
import io.exoquery.SqlQuery
import io.exoquery.fansi.Attrs
import io.exoquery.kmp.pprint.PPrinter
import io.exoquery.kmp.pprint.PPrinterManual
import io.exoquery.pprint.PPrinterConfig
import io.exoquery.pprint.Tree
import io.exoquery.util.ShowTree
import io.exoquery.xr.*
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.serializer

fun <T> iteratorOf(vararg elements: T): Iterator<T> = elements.iterator()

object PrintXRType {
  val BlackWhite = PrintXR(XRType.serializer(), PrintXR.defaultConfig)
}

fun qprint(xr: XR) = PrintXR.Color.invoke(xr)

class PrintMisc(config: PPrinterConfig = PPrinterConfig()): PPrinterManual<Any?>(config) {
  fun treeifyThis(x: Any?, elementName: String?) = treeify(x, elementName, escapeUnicode = config.defaultEscapeUnicode, showFieldNames = config.defaultShowFieldNames)

  override fun treeify(x: Any?, elementName: String?, escapeUnicode: Boolean, showFieldNames: Boolean): Tree =
    when (x) {
      is XR -> PrintXR(XR.serializer(), config.copy(defaultShowFieldNames = false)).treeifyThis(x, elementName)
      is XRType -> PrintXR(XRType.serializer(), config).treeifyThis(x, elementName)
      is SqlExpression<*> -> Tree.Apply("SqlExpression", iteratorOf(treeifyThis(x.xr, "xr"), treeifyThis(x.runtimes, "runtimes"), treeifyThis(x.params, "params")))
      is SqlQuery<*> -> Tree.Apply("SqlQuery", iteratorOf(treeifyThis(x.xr, "xr"), treeifyThis(x.runtimes, "runtimes"), treeifyThis(x.params, "params")))
      is Params -> Tree.Apply("Params", x.lifts.map { l -> Tree.KeyValue(l.id.value, Tree.Literal(l.value.toString())) }.iterator())
      is Runtimes -> Tree.Apply("Runtimes", x.runtimes.map { (id, xr) -> Tree.KeyValue(id.value, treeifyThis(xr, null)) }.iterator())
      is ShowTree -> x.showTree(config)
      else -> super.treeify(x, elementName, escapeUnicode, showFieldNames)
    }
}

// Simple printer for things like SelectClause that skip the loc/file fields. In the future may want to make skippable fields configurable in the constructor
class PrintSkipLoc<T>(serializer: SerializationStrategy<T>, config: PPrinterConfig = PrintXR.defaultConfig): PPrinter<T>(serializer, config) {
  override fun <E> treeifyComposite(elem: Treeifyable.Elem<E>, elementName: String?, showFieldNames: Boolean): Tree =
    when (val x = elem.value) {
      is XR -> PrintXR(XR.serializer(), config).treeifyThis(x, elementName)
      else ->
        when (val tree = super.treeifyComposite(elem, elementName, showFieldNames)) {
          is Tree.Apply -> {
            val superNodes = tree.body.asSequence()
              .filterNot { it.elementName == "loc" }
              .filterNot { it.elementName == "file" }
            Tree.Apply(tree.prefix, superNodes.iterator(), elementName)
          }
          else -> tree
        }
    }
}

class PrintXR<T>(serializer: SerializationStrategy<T>, config: PPrinterConfig = defaultConfig): PPrinter<T>(serializer, config) {

  fun treeifyThis(x: T, elementName: String?) =
    treeify(x, elementName, escapeUnicode = config.defaultEscapeUnicode, showFieldNames = defaultConfig.defaultShowFieldNames)

  override fun <E> treeifyComposite(elem: Treeifyable.Elem<E>, elementName: String?, showFieldNames: Boolean): Tree =
    when (val x = elem.value) {
      is XR ->
        // NEVER to super.treeifyComposite(Treeifyable.Elem(x, serializer<XR>()), ...) as that will cause infinite recursion
        // that is because For a polymorphic hierarchy such as:
        // sealed interface Colors { object Red: Colors; data class Custom(val hex: String): Colors }
        // the json-serialized form of Colors.Red will be:
        // { "type": "Red", "value": {} } which in pprint will appear as Red(type="package.Red", value=Red)
        // For the class `Custom` it will be:
        // { "type": "Custom", "value": { "hex": "FF0000" } } which in pprint will appear as Custom(type="package.Custom", value=Custom(hex="FF0000"))
        // now when it is recursing inside the outer object the serializer it will call treeifyElement which will recurse inside here.
        // Then the `serializer<XR>` will create the same outer-serializer again and the cycle will go on forever.
        when (val tree = super.treeifyComposite(elem, elementName, showFieldNames)) {
          is Tree.Apply -> {
            val superNodes = tree.body.asSequence()
              .filterNot { it.elementName == "loc" }
              .filterNot { it.elementName == "file" }
            Tree.Apply(tree.prefix, superNodes.iterator(), elementName)
          }
          else -> tree
        }
      is ShowTree -> x.showTree(config)
      else -> super.treeifyComposite(elem, elementName, showFieldNames)
    }


  override fun <R> treeifyValueOrNull(x: R, elementName: String?, escapeUnicode: Boolean, showFieldNames: Boolean): Tree? = run {
    when (x) {
      is XRType ->
        when (x) {
          is XRType.Product -> Tree.Literal("${x.name.takeLastWhile { it != '.' }}(...)", elementName)
          else -> Tree.Literal(x.shortString(), elementName)
        }
      //is XR.Product -> Tree.Apply("Product", (listOf(treeifyThis(x.name.takeLastWhile { it != '.' })) + x.fields.map { treeifyThis(it) }).iterator())
      //is XR.Infix -> Tree.Apply("Infix", iteratorOf(treeifySuper(x.parts), treeifySuper(x.params)))
      is XR.Ident -> Tree.Apply("Id", iteratorOf(Tree.Literal(x.name, "name"), PrintXR(XRType.serializer(), config).treeifyThis(x.type, "type")), elementName)
      is XR.Location.File -> Tree.Literal("<Location:${x.path}:${x.row}:${x.col}>", elementName)
      is XR.Location.Synth -> Tree.Literal("<Location:Synthetic>", elementName)

      is XR.Const.String -> Tree.Apply("String", iteratorOf(Tree.Literal(x.value, null)), elementName)
      is XR.Const.Int -> Tree.Apply("Int", iteratorOf(Tree.Literal("${x.value}", null)), elementName)
      is XR.Const.Double -> Tree.Apply("Double", iteratorOf(Tree.Literal("${x.value}", null)), elementName)
      is XR.Const.Boolean -> Tree.Apply("Bool", iteratorOf(Tree.Literal("${x.value}", null)), elementName)
      is XR.Const.Null -> Tree.Apply("Null", iteratorOf(), elementName)
      is XR.Const.Char -> Tree.Apply("Char", iteratorOf(Tree.Literal("${x.value}", null)), elementName)
      is XR.Const.Byte -> Tree.Apply("Byte", iteratorOf(Tree.Literal("${x.value}", null)), elementName)
      is XR.Const.Short -> Tree.Apply("Short", iteratorOf(Tree.Literal("${x.value}", null)), elementName)
      is XR.Const.Long -> Tree.Apply("Long", iteratorOf(Tree.Literal("${x.value}", null)), elementName)
      is XR.Const.Float -> Tree.Apply("Float", iteratorOf(Tree.Literal("${x.value}", null)), elementName)


      //is DistinctKind -> Tree.Literal(x::class.simpleName ?: "BinaryOp?")
      is Operator -> Tree.Literal(x.symbol, elementName)
      //is PC<*> -> Tree.Apply(x::class.simpleName ?: "PC?", run {
      //  when (val pc = x.productComponents) {
      //    is ProductClass0 -> iteratorOf()
      //    is ProductClass1<*, *> -> iteratorOf(treeifyThis(pc.a))
      //    is ProductClass2<*, *, *> -> iteratorOf(treeifyThis(pc.a), treeifyThis(pc.b))
      //    is ProductClass2M<*, *, *, *> -> iteratorOf(treeifyThis(pc.a), treeifyThis(pc.m), treeifyThis(pc.b))
      //    else -> iteratorOf()
      //  }
      //})
      else -> null
    }
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