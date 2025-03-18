package io.exoquery.xr

import io.decomat.*
import io.exoquery.BID

// Can't use || or && chars because they don't work with linuxX64
infix fun XR.Expression.`+or+`(other: XR.Expression): XR.BinaryOp = XR.BinaryOp(this, OP.or, other, this.loc)
infix fun XR.Expression.`+and+`(other: XR.Expression): XR.BinaryOp = XR.BinaryOp(this, OP.and, other, this.loc)
infix fun XR.Expression.`+==+`(other: XR.Expression): XR.BinaryOp = XR.BinaryOp(this, OP.`==`, other, this.loc)
infix fun XR.Expression.`+!=+`(other: XR.Expression): XR.BinaryOp = XR.BinaryOp(this, OP.`!=`, other, this.loc)
infix fun XR.Expression.`+'+'+`(other: XR.Expression): XR.BinaryOp = XR.BinaryOp(this, OP.strPlus, other, this.loc)
infix fun XR.Expression.`+++`(other: XR.Expression): XR.BinaryOp = XR.BinaryOp(this, OP.plus, other, this.loc)

fun XR.Query.swapTags(tagMap: Map<BID, BID>): XR.Query =
  SwapTagsTransformer(tagMap).invoke(this)

fun XR.Expression.swapTags(tagMap: Map<BID, BID>): XR.Expression =
  SwapTagsTransformer(tagMap).invoke(this)

fun XR.Action.swapTags(tagMap: Map<BID, BID>): XR.Action =
  SwapTagsTransformer(tagMap).invoke(this)

fun XR.Batching.swapTags(tagMap: Map<BID, BID>): XR.Batching =
  SwapTagsTransformer(tagMap).invoke(this)

internal class SwapTagsTransformer(tagMap: Map<BID, BID>): TransformXR(
  transformExpression = {
    with (it) {
      when (this) {
        is XR.TagForParam ->
          tagMap.get(id)?.let { XR.TagForParam.csf(it)(this) }
        is XR.TagForSqlExpression -> tagMap.get(id)?.let { XR.TagForSqlExpression.csf(it)(this) }
        // Transform XR expects a "partial function" i.e. for the user to return 'null'
        // when there is no transform for the given tree. Then it knows to recurse further in.
        else -> null
      }
    }
  },
  transformQuery = {
    with(it) {
      when (this) {
        is XR.TagForSqlQuery -> tagMap.get(id)?.let { XR.TagForSqlQuery.csf(it)(this) }
        else -> null
      }
    }
  }
)

fun XR.Map.containsImpurities(): Boolean =
  CollectXR(this) {
    with(it) {
      when {
        this is XR.GlobalCall && !this.isPure() -> this
        this is XR.MethodCall && !this.isPure() -> this
        this is XR.Free && !this.pure -> this
        else -> null
      }
    }
  }.isNotEmpty()


/*
def unapply(ast: Ast) =
CollectAst(ast) {
  case agg: Aggregation          => agg
    case inf: Free if (!inf.pure) => inf
}.nonEmpty
 */

// case(When.SingleBranch[Is(), Is()])

val XR.When.Companion.SingleBranch get() = SingleBranch()
class SingleBranch {
  operator fun <AP: Pattern<A>, A: XR.Expression, BP: Pattern<XR.Expression>> get(x: AP, y: BP) =
    customPattern2("When.SingleBranch", x, y) { it: XR.When ->
      with(it) {
        when {
          this.branches.size == 1 -> Components2(this.branches[0].cond, this.branches[0].then)
          else -> null
        }
      }
    }
}

val XR.When.Companion.NullIfNullOrX get() = NullIfNullOrX()
class NullIfNullOrX {
  operator fun <AP: Pattern<XR.Expression>> get(x: AP) =
    customPattern1("When.NullIfNullOrX", x) { it: XR.When ->
      on(it).match(
        /* Simple example:
         * ```
         * val x = from(...)
         * val y = joinLeft(...)
         * y?.foo
         * ```
         * The `y?.foo` is interpreted in Kotlin is `if (y == null) null else y.foo`
         * so in this case we want to just use y.foo because y itself cannot be null in the SQL
         */
        case(XR.When.SingleBranch[XR.BinaryOp.EqEq[Is(), NullXR()], NullXR()]).thenThis { _, _ ->
          Components1(this.orElse)
        }
      )
    }
}

val XR.When.Companion.XIsNotNullOrNull get() = XIsNotNullOrNull()
class XIsNotNullOrNull {
  operator fun <AP: Pattern<XR.Expression>> get(x: AP) =
    customPattern1("When.XIsNotNullOrNull", x) { it: XR.When ->
      on(it).match(
        // if (x != null) x else null -> x
        case(XR.When.SingleBranch[XR.BinaryOp.NotEq[Is(), NullXR()], Is()]).thenIfThis { _, _ -> NullXR().matchesAny(orElse) }.thenThis { _, then ->
          Components1(then)
        }
      )
    }
}

fun IsTypeProduct() = Is<XR.Expression> { it.type is XRType.Product }
fun NullXR() = Is<XR.Expression> { it is XR.Const.Null }

val XR.BinaryOp.Companion.NotEq get() = BinaryOpNotEq()
class BinaryOpNotEq {
  operator fun <AP: Pattern<XR.Expression>, BP: Pattern<XR.Expression>> get(x: AP, y: BP) =
    customPattern2("When.BinaryOpNotEq", x, y) { it: XR.BinaryOp ->
      on(it).match(
        case(XR.BinaryOp[Is(), Is()]).thenThis { a, op, b ->
          if (op is OP.`!=`) Components2(a, b) else null
        }
      )
    }
}

val XR.BinaryOp.Companion.EqEq get() = BinaryOpEqEq()
class BinaryOpEqEq {
  operator fun <AP: Pattern<XR.Expression>, BP: Pattern<XR.Expression>> get(x: AP, y: BP) =
    customPattern2("When.BinaryOpEqEq", x, y) { it: XR.BinaryOp ->
      on(it).match(
        case(XR.BinaryOp[Is(), Is()]).thenThis { a, op, b ->
          if (op is OP.`==`) Components2(a, b) else null
        }
      )
    }
}


val XR.Map.Companion.DistinctHead get() = DistinctHeadMap()
class DistinctHeadMap() {
  operator fun <AP: Pattern<Q>, Q: XR.Query, BP: Pattern<XR.Expression>> get(x: AP, y: BP) =
    customPattern2M("XR.Map.DistinctHead", x, y) { it: XR.Map ->
      with(it) {
        when {
          this.head is XR.Distinct -> Components2M(this.head.head, this.id, this.body)
          else -> null
        }
      }
    }
}


val XR.SortBy.Companion.DistinctHead get() = DistinctHeadMatchSortBy()
class DistinctHeadMatchSortBy() {
  operator fun <AP: Pattern<Q>, Q: XR.Query, BP: Pattern<XR.Expression>> get(x: AP, y: BP) =
    customPattern2M("XR.SortBy.DistinctHead", x, y) { it: XR.SortBy ->
      with(it) {
        when {
          this.head is XR.Distinct -> Components2M(this.head.head, this.id, this.criteria)
          else -> null
        }
      }
    }
}


fun XR.When.nestProperty(propertyName: String): XR.When =
  XR.When(this.branches.map { it.copy(then = XR.Property(it.then, propertyName)) }, XR.Property(this.orElse, propertyName))


object CID {
  val kotlin_String = XR.ClassId("kotlin.String")
  val kotlin_Int = XR.ClassId("kotlin.Int")
  val kotlin_Long = XR.ClassId("kotlin.Long")
  val kotlin_Short = XR.ClassId("kotlin.Short")
  val kotlin_Double = XR.ClassId("kotlin.Double")
  val kotlin_Float = XR.ClassId("kotlin.Float")
  val kotlin_Boolean = XR.ClassId("kotlin.Boolean")
}

fun XR.ClassId.isWholeNumber(): Boolean =
  when(this) {
    CID.kotlin_Int, CID.kotlin_Long, CID.kotlin_Short -> true
    else -> false
  }

fun XR.ClassId.isFloatingPoint(): Boolean =
  when(this) {
    CID.kotlin_Double, CID.kotlin_Float -> true
    else -> false
  }

fun XR.ClassId.isNumeric(): Boolean =
  when(this) {
    CID.kotlin_Int, CID.kotlin_Long, CID.kotlin_Short, CID.kotlin_Double, CID.kotlin_Float -> true
    else -> false
  }

fun String.isConverterFunction(): Boolean =
  when(this) {
    "toLong", "toInt", "toShort", "toDouble", "toFloat", "toBoolean" -> true
    else -> false
  }
