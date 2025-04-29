package io.exoquery.comapre

import io.exoquery.fansi.Bold
import io.exoquery.fansi.Str
import io.exoquery.pprint.*
import io.exoquery.pprint.Tree.Apply
import io.exoquery.pprint.Tree.KeyValue
import io.exoquery.printing.PrintXR
import io.exoquery.xr.XR
import io.exoquery.fansi.Color as FansiColor

class PrintDiff(val defaultWidth: Int = 150) : PPrinter(
  PPrinterConfig(
    defaultWidth = defaultWidth,
    defaultIndent = 2
  )
) {

//  def apply(x: Any, verbose: Boolean = false): fansi.Str = {
//    this.verbose = verbose
//    val tokenized = this.tokenize(x).toSeq
//    fansi.Str.join(tokenized)
//  }
//
//  def tokenize(x: Any): Iterator[fansi.Str] = {
//    val tree      = this.treeify(x)
//    val renderer  = new Renderer(defaultWidth, colorApplyPrefix, colorLiteral, defaultIndent)
//    val rendered  = renderer.rec(tree, 0, 0).iter
//    val truncated = new Truncated(rendered, defaultWidth, defaultHeight)
//    truncated
//  }
//

  // Show field names e.g.
  // Property(ir = Ident(name = "inst"), name = "currentDate") vs
  // Property(Ident("inst"), "currentDate")
  val showFieldNames = false
  val escapeUnicode = false
  val defaultHeight: Int = Integer.MAX_VALUE
  val defaultIndent: Int = 2
  var verbose: Boolean = false

  fun apply(x: Any, verbose: Boolean = false): Str {
    this.verbose = verbose
    val tokenized = this.tokenize(x).toList()
    return Str.join(tokenized)
  }

  fun tokenize(x: Any): List<Str> {
    val tree = this.treeify(x, null, escapeUnicode, showFieldNames)
    val renderer = Renderer(defaultWidth, config.colorApplyPrefix, config.colorLiteral, defaultIndent)
    val rendered = renderer.rec(tree, 0, 0).iter
    val truncated = Truncated(rendered, defaultWidth, defaultHeight)
    return truncated.toResult().iter.asSequence().toList()
  }

  // Scala:
  // def treeify(x: Any): Tree = this.treeify(x, escapeUnicode, showFieldNames)
  fun treeify(x: Any): Tree = this.treeify(x, null, escapeUnicode, showFieldNames)

// Scala:
//  /**
//   * In the situation where one tree is subtantially larger than another (e.g. 1-line vs 50-lines) it typically does not
//   * make sense to show the larger tree in its entirely because the difference is at the 'tips' of the trees. In the
//   * case of MorphirJsonDecoding specs showing the larger tree in its entirety would introduce such a large amount of
//   * noise that the logs became impractical to use.
//   */
//  def truncateSmallerTree(aRaw: Tree, bRaw: Tree) = {
//    // Need to make duplicates to do countMaxDepth because it is destructive because the Tree has mutable iterators
//    val (a, aDup) = duplicateTree(aRaw)
//    val (b, bDup) = duplicateTree(bRaw)
//
//    val (smallLabel, smallTree, smallDepth) :: (largeLabel, largeTree, largeDepth) :: Nil =
//      (List(("a", a, countMaxDepth(aDup)), ("b", b, countMaxDepth(bDup))).sortBy(_._3)): @unchecked
//
//    // Activate this logic if one tree is 5x as large as the other or bigger
//    val largeTreeTruncated =
//      if (largeDepth >= smallDepth * 5) {
//        truncateDepth(largeTree, smallDepth * 5)
//      } else {
//        largeTree
//      }
//
//    // put them back into a/b order by sort by the labels which will be either "a", or "b"
//    val (_, aOut) :: (_, bOut) :: Nil =
//      // NOTE: Please verify this is correct had to be marked with @unchecked to eliminate a warning
//      (List((smallLabel, smallTree), (largeLabel, largeTreeTruncated)).sortBy(_._1)): @unchecked
//
//    (aOut, bOut)
//  }

  /**
   * In the situation where one tree is subtantially larger than another (e.g. 1-line vs 50-lines) it typically does not
   * make sense to show the larger tree in its entirely because the difference is at the 'tips' of the trees. In the
   * case of MorphirJsonDecoding specs showing the larger tree in its entirety would introduce such a large amount of
   * noise that the logs became impractical to use.
   */
  fun truncateSmallerTree(aRaw: Tree, bRaw: Tree): Pair<Tree, Tree> {
    // Need to make duplicates to do countMaxDepth because it is destructive because the Tree has mutable iterators
    val (a, aDup) = duplicateTree(aRaw)
    val (b, bDup) = duplicateTree(bRaw)

    val (smallThings, largeThings) =
      listOf(
        Triple("a", a, countMaxDepth(aDup)), Triple("b", b, countMaxDepth(bDup))
      ).sortedBy { it.third }

    val (smallLabel, smallTree, smallDepth) = smallThings
    val (largeLabel, largeTree, largeDepth) = largeThings

    val truncationFactor = 8

    fun doTruncation(smallDepth: Int, largeDepth: Int) =
      // if the trees are both tiny we don't care
      if (smallDepth < 200 && largeDepth < 200) false
      else if (largeDepth >= smallDepth * truncationFactor) true
      else false

    // Activate this logic if one tree is 5x as large as the other or bigger
    val largeTreeTruncated =
      if (doTruncation(smallDepth, largeDepth)) {
        truncateDepth(largeTree, smallDepth * truncationFactor)
      } else {
        largeTree
      }

    // put them back into a/b order by sort by the labels which will be physically "a", or "b"
    // (NOTE: much more efficient would be just to do if smallLabel == "a" then (smallTree, largeTreeTruncated) else (largeTreeTruncated, smallTree))
    // not doing it for now beause this is just a comparison utility, shuold not be run online in code)
    val (smallOut, largeOut) = listOf(
      Pair(smallLabel, smallTree),
      Pair(largeLabel, largeTreeTruncated)
    ).sortedBy { it.first }

    return Pair(smallOut.second, largeOut.second)
  }


  // Scala:
//def duplicateTree(tree: Tree): (Tree, Tree) =
//  tree match {
//    case Apply(prefix, body) =>
//    val (aBody, bBody) = body.toList.map(duplicateTree(_)).unzip
//    (Apply(prefix, aBody.iterator), Apply(prefix, bBody.iterator))
//
//    case KeyValue(key, value) =>
//    val (value1, value2) = duplicateTree(value)
//    (KeyValue(key, value1), KeyValue(key, value2))
//
//    case l @ Lazy(_) => (l, l)
//
//    case Free(lhs, op, rhs) =>
//    val (lhs1, lhs2) = duplicateTree(lhs)
//    val (rhs1, rhs2) = duplicateTree(rhs)
//    (Free(lhs1, op, rhs1), Free(lhs2, op, rhs2))
//
//    case l @ Tree.Literal(_) => (l, l)
//  }
//
//// IMPORTANT - This function is actually destructive since it will make the Tree.Apply.body's iterator go to the end,
//// make sure to copy the tree before doing it
//def countMaxDepth(tree: Tree, currDepth: Int = 0): Int =
//tree match {
//  case Apply(_, body)     => body.toList.map(countMaxDepth(_, currDepth + 1)).maxOption.getOrElse(0)
//  case KeyValue(_, value) => countMaxDepth(value, currDepth + 1)
//  case Lazy(_)            => currDepth // don't know what to do about lazy trees for now
//  case Free(lhs, _, rhs) => Math.max(countMaxDepth(lhs, currDepth + 1), countMaxDepth(rhs, currDepth + 1))
//  case Tree.Literal(_)    => currDepth
//}

  fun duplicateTree(tree: Tree): Pair<Tree, Tree> {
    return when (tree) {
      is Apply -> {
        val (aBody, bBody) = tree.body.asSequence().map { duplicateTree(it) }.unzip()
        Pair(Apply(tree.prefix, aBody.iterator()), Apply(tree.prefix, bBody.iterator()))
      }

      is KeyValue -> {
        val (value1, value2) = duplicateTree(tree.value)
        Pair(KeyValue(tree.key, value1), KeyValue(tree.key, value2))
      }

      is Tree.Lazy -> Pair(tree, tree)
      is Tree.Infix -> {
        val (lhs1, lhs2) = duplicateTree(tree.lhs)
        val (rhs1, rhs2) = duplicateTree(tree.rhs)
        Pair(Tree.Infix(lhs1, tree.op, rhs1), Tree.Infix(lhs2, tree.op, rhs2))
      }

      is Tree.Literal -> Pair(tree, tree)
    }
  }

  fun countMaxDepth(tree: Tree, currDepth: Int = 0): Int {
    return when (tree) {
      is Apply -> tree.body.asSequence().map { countMaxDepth(it, currDepth + 1) }.maxOrNull() ?: 0
      is KeyValue -> countMaxDepth(tree.value, currDepth + 1)
      is Tree.Lazy -> currDepth // don't know what to do about lazy trees for now
      is Tree.Infix -> maxOf(countMaxDepth(tree.lhs, currDepth + 1), countMaxDepth(tree.rhs, currDepth + 1))
      is Tree.Literal -> currDepth
    }
  }

  fun truncateDepth(tree: Tree, truncateDepth: Int): Tree {
    fun truncateDepthRec(tree: Tree, currDepth: Int): Tree {
      return if (currDepth >= truncateDepth) {
        Tree.Literal("...")
      } else {
        when (tree) {
          is Apply -> Apply(tree.prefix, tree.body.asSequence().map { truncateDepthRec(it, currDepth + 1) }.iterator())
          is Tree.Lazy -> tree // don't do anything ro lazy values
          is Tree.Infix -> Tree.Infix(
            truncateDepthRec(tree.lhs, currDepth + 1),
            tree.op,
            truncateDepthRec(tree.rhs, currDepth + 1)
          )

          is Tree.Literal -> tree
          is KeyValue -> KeyValue(tree.key, truncateDepthRec(tree.value, currDepth + 1))
        }
      }
    }

    return truncateDepthRec(tree, 0)
  }

// Scala
//  override def treeify(x: Any, escapeUnicode: Boolean, showFieldNames: Boolean): Tree = x match {
//    case l: Compare.Diff.Leaf =>
//      val leftRaw  = treeify(l.a, escapeUnicode, showFieldNames)
//      val rightRaw = treeify(l.b, escapeUnicode, showFieldNames)
//      // after invoking the below function cannot use leftRaw, rightRaw anymore because Tree.Apply uses iterators
//      val (a, b) = truncateSmallerTree(leftRaw, rightRaw)
//      Tree.Apply(
//        "Diff",
//        List(
//          Tree.KeyValue("left", a),
//          Tree.KeyValue("right", b)
//        ).iterator
//      )
//
//    case l: Compare.Diff.Leaf2 =>
//      val leftRaw  = treeify(l.a, escapeUnicode, showFieldNames)
//      val rightRaw = treeify(l.b, escapeUnicode, showFieldNames)
//      // after invoking the below function cannot use leftRaw, rightRaw anymore because Tree.Apply uses iterators
//      val (a, b) = truncateSmallerTree(leftRaw, rightRaw)
//      Tree.Apply(
//        "Diff",
//        List(
//          Tree.KeyValue("left", a),
//          Tree.KeyValue("right", b)
//        ).iterator
//      )
//

  fun Red(value: String): String = FansiColor.Red(value).toString()
  fun Blue(value: String): String = FansiColor.Blue(value).toString()
  private fun LeftKV(value: Tree): KeyValue = KeyValue(Bold.On(FansiColor.Blue("LEFT")).toString(), value)
  private fun RightKV(value: Tree): KeyValue = KeyValue(Bold.On(FansiColor.Red("RIGHT")).toString(), value)

  override fun treeify(x: Any?, elementName: String?, escapeUnicode: Boolean, showFieldNames: Boolean): Tree =
    when (x) {
      is Compare.Diff.Match ->
        Apply(
          "Match",
          listOf(
            LeftKV(Tree.Literal(x.leftValue.toString() ?: "<Null>")),
            RightKV(Tree.Literal(x.rightValue.toString() ?: "<Null>"))
          ).iterator()
        )

      is Compare.Diff.Leaf -> {
        val leftRaw = treeify(x.a, null, escapeUnicode, showFieldNames)
        val rightRaw = treeify(x.b, null, escapeUnicode, showFieldNames)
        val (a, b) = truncateSmallerTree(leftRaw, rightRaw)
        Apply(
          "Diff (Leaf)",
          listOf(
            LeftKV(a),
            RightKV(b)
          ).iterator()
        )
      }

      is Compare.Diff.Leaf2 -> {
        val leftRaw = treeify(x.a, null, escapeUnicode, showFieldNames)
        val rightRaw = treeify(x.b, null, escapeUnicode, showFieldNames)
        val (a, b) = truncateSmallerTree(leftRaw, rightRaw)
        Apply(
          "Diff (Leaf-2cls)",
          listOf(
            LeftKV(a),
            RightKV(b)
          ).iterator()
        )
      }

//    case s: Compare.Diff.Set =>
//      Tree.Apply(
//        s.typename,
//        List(
//          Tree.Apply("onlyLeft", s.onlyLeft.toList.map(treeify(_, escapeUnicode, showFieldNames)).iterator),
//          Tree.Apply("onlyRight", s.onlyRight.toList.map(treeify(_, escapeUnicode, showFieldNames)).iterator)
//        ).iterator
//      )
//

      is Compare.Diff.Set -> {
        Apply(
          x.typename,
          listOf(
            Apply(
              Blue("ONLY_LEFT"),
              x.onlyLeft.toList().map { treeify(it, null, escapeUnicode, showFieldNames) }.iterator()
            ),
            Apply(
              Red("ONLY_RIGHT"),
              x.onlyRight.toList().map { treeify(it, null, escapeUnicode, showFieldNames) }.iterator()
            )
          ).iterator()
        )
      }
//    case m: Compare.Diff.MissingLeft =>
//      Tree.Apply(
//        "Diff",
//        List(
//          Tree.KeyValue("left", Tree.Literal("Missing")),
//          Tree.KeyValue("right", treeify(m.rightValue, escapeUnicode, showFieldNames))
//        ).iterator
//      )
//
//    case m: Compare.Diff.MissingRight =>
//      Tree.Apply(
//        "Diff",
//        List(
//          Tree.KeyValue("left", treeify(m.leftValue, escapeUnicode, showFieldNames)),
//          Tree.KeyValue("right", Tree.Literal("Missing"))
//        ).iterator
//      )
//
      is Compare.Diff.MissingLeft -> {
        Apply(
          "Diff",
          listOf(
            LeftKV(Tree.Literal("Missing")),
            RightKV(treeify(x.rightValue, null, escapeUnicode, showFieldNames))
          ).iterator()
        )
      }

      is Compare.Diff.MissingRight -> {
        Apply(
          "Diff",
          listOf(
            LeftKV(treeify(x.leftValue, null, escapeUnicode, showFieldNames)),
            RightKV(Tree.Literal("Missing"))
          ).iterator()
        )
      }

//    case s: Compare.Diff.Sequence =>
//      Tree.Apply(
//        s.typename,
//        s.fields.toList
//          .sortBy(_._1)
//          .map { case (key, diff) =>
//            Tree.KeyValue(key, treeify(diff, escapeUnicode, showFieldNames))
//          }
//          .iterator
//      )
//
//    case o: Compare.Diff.Object =>
//      Tree.Apply(
//        o.typename,
//        o.fields.map { case (name, value) =>
//          Tree.KeyValue(name, treeify(value, escapeUnicode, showFieldNames))
//        }.iterator
//      )
//
//    case other => super.treeify(other, escapeUnicode, showFieldNames)
//  }
//}

      is Compare.Diff.Sequence -> {
        Apply(
          x.typename,
          x.fields.toList()
            .sortedBy { it.first }
            .map { (key, diff) ->
              KeyValue(key, treeify(diff, null, escapeUnicode, showFieldNames))
            }
            .iterator()
        )
      }

      is Compare.Diff.Object -> {
        Apply(
          x.typename,
          x.fields.map { (name, value) ->
            KeyValue(name, treeify(value, null, escapeUnicode, showFieldNames))
          }.iterator()
        )
      }

      is XR -> PrintXR(XR.serializer(), config).treeify(x, elementName, escapeUnicode, showFieldNames)

      // Getting lots of error from the field seen0 from the parent level printout in pprint ProductSupport so for now just use toString to avoid:
      // io.exoquery.BasicSelectClauseQuotationSpec[jvm] > parsing features spec[jvm] > from + join[jvm] FAILED
      //    java.lang.IllegalStateException: The parameter name 'seen0' of io.exoquery.xr.XR.Location.File could not be found within the list of members: [val io.exoquery.xr.XR.Location.File.col: kotlin.Int, val io.exoquery.xr.XR.Location.File.path: kotlin.String, val io.exoquery.xr.XR.Location.File.row: kotlin.Int]
      //else -> super.treeify(x, elementName, escapeUnicode, showFieldNames)

      else -> Tree.Literal(x.toString(), elementName)
    }
}
