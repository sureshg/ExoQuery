package io.exoquery.comapre

import io.exoquery.util.toLinkedMap
import io.exoquery.xr.SX
import io.exoquery.xr.XR
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.allSuperclasses
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberProperties
import kotlin.collections.Set as KSet

fun Compare.Diff?.show(defaultIdent: Int = 150) =
  if (this == null)
    "No differences found"
  else
    PrintDiff(defaultIdent).invoke(this)

class Compare(val showSuccess: Boolean = false, val skipFields: List<String> = listOf()) {
  operator fun invoke(a: Any?, b: Any?): Diff? =
    generic(a, b)

//
//    object Leaf {
//      def of[T](a: T, b: T)(implicit ct: ClassTag[T]): Option[Diff] =
//        if (a == b)
//          None
//        else
//          Some(Leaf(className(ct.runtimeClass), a, b))
//      def ofClass(aCls: Class[_], bCls: Class[_])(a: Any, b: Any): Option[Diff] =
//        if (aCls != bCls && a != b)
//          Some(Leaf2(className(aCls), className(bCls), a, b))
//        else if (aCls == bCls && a != b)
//          Some(Leaf(className(aCls), a, b))
//        else
//          None
//    }
//  }

  sealed interface Diff {

    // TODO maybe in some situations where an actual key is missing from a product
    //      we might want to have a special case for that i.e. MissingKey should look into it

    data class Match(val leftValue: Any?, val rightValue: Any?): Diff

    data class MissingRight(val leftValue: Any): Diff
    data class MissingLeft(val rightValue: Any): Diff

    data class Object(val typename: String, val fields: LinkedHashMap<String, Diff>): Diff
    data class Sequence(val typename: String, val fields: LinkedHashMap<String, Diff>): Diff

    data class Leaf(val typename: String, val a: Any, val b: Any): Diff
    data class Leaf2(val typenameA: String, val typenameB: String, val a: Any, val b: Any): Diff

    data class Set(val typename: String, val onlyLeft: KSet<Any?>, val onlyRight: KSet<Any?>): Diff
  }

  fun Diff?.withField(field: String): Pair<String, Diff>? =
    this?.let { field to it }

  // NOTE: Generalize the XR, SX, parts of it since we want it to be more geneic that just XR, SX
  val Any?.isData get() = this?.let { it::class.isData || it is XR || it is SX } ?: false
  val Any?.isAnyRef get() = this?.let { !it::class.isValue } ?: false

//  def apply(a: Any, b: Any): Option[Diff] = generic(a, b)
//  private def generic(aValue: Any, bValue: Any): Option[Diff] =
//    (aValue, bValue) match {
//      case _ if (aValue == bValue) => None
//      // Needs specialized by-content comparator for sets
//      case (a: Set[_], b: Set[_])           => set(a, b)
//      case (a: Iterable[_], b: Iterable[_]) => iterable(a, b)
//      case (a: Int, b: Int)                 => Diff.Leaf.of(a, b)
//      case (a: Short, b: Short)             => Diff.Leaf.of(a, b)
//      case (a: Boolean, b: Boolean)         => Diff.Leaf.of(a, b)
//      case (a: Double, b: Double)           => Diff.Leaf.of(a, b)
//      case (a: Float, b: Float)             => Diff.Leaf.of(a, b)
//      case (a: Byte, b: Byte)               => Diff.Leaf.of(a, b)
//      case (a: Char, b: Char)               => Diff.Leaf.of(a, b)
//      case (a: Product, b: Product)         => product(a, b)
//      case (a: AnyRef, b: AnyRef)           => Diff.Leaf.ofClass(a.getClass, b.getClass)(a, b)
//    }

  fun matchSuccess(aValue: Any?, bValue: Any?): Diff? =
    if (showSuccess) {
      Diff.Match(aValue, bValue)
    } else {
      null
    }

  fun generic(aValue: Any?, bValue: Any?): Diff? = run {
    val out = when {
      // Go back to this
      //aValue == bValue -> null
      aValue == bValue -> matchSuccess(aValue, bValue)
      // technically covered by 1st case but just want to state it explicitly
      aValue == null && bValue == null -> matchSuccess(aValue, bValue)
      aValue != null && bValue == null -> Diff.MissingRight(aValue)
      aValue == null && bValue != null -> Diff.MissingLeft(bValue)
      aValue is KSet<*> && bValue is KSet<*> -> set(aValue, bValue)
      aValue is Iterable<*> && bValue is Iterable<*> -> iterable(aValue, bValue)
      aValue is Int && bValue is Int -> Diff.Leaf("Int", aValue, bValue)
      aValue is Short && bValue is Short -> Diff.Leaf("Short", aValue, bValue)
      aValue is Boolean && bValue is Boolean -> Diff.Leaf("Boolean", aValue, bValue)
      aValue is Double && bValue is Double -> Diff.Leaf("Double", aValue, bValue)
      aValue is Float && bValue is Float -> Diff.Leaf("Float", aValue, bValue)
      aValue is Byte && bValue is Byte -> Diff.Leaf("Byte", aValue, bValue)
      aValue is Char && bValue is Char -> Diff.Leaf("Char", aValue, bValue)
      aValue is String && bValue is String -> Diff.Leaf("String", aValue, bValue)
      aValue.isData && bValue.isData -> productNullable(aValue, bValue)
      // NOTE: the aValue.isAnyRef will return `false` if the value is null so can do !! in the body
      else ->
        Diff.Leaf2(
          aValue!!::class.simpleName ?: aValue::class.qualifiedName ?: "Value",
          bValue!!::class.simpleName ?: bValue::class.qualifiedName ?: "Value",
          aValue,
          bValue
        )
    }
    //println("------ Compare: $aValue, $bValue -> isNull: ${out == null}")
    out
  }

//
//  private def set(ai: Set[_], bi: Set[_]): Option[Diff] = {
//    val a     = ai.asInstanceOf[Set[Any]]
//    val b     = bi.asInstanceOf[Set[Any]]
//    val onlyA = a.removedAll(b)
//    val onlyB = b.removedAll(a)
//    if (onlyA.isEmpty && onlyB.isEmpty)
//      None
//    else
//      Some(Diff.Set("Set", onlyA, onlyB))
//  }

  fun set(a: KSet<*>, b: KSet<*>): Diff? {
    val onlyA = a - b
    val onlyB = b - a
    return if (onlyA.isEmpty() && onlyB.isEmpty())
      null
    else
      Diff.Set("Set", onlyA, onlyB)
  }

  // Super quick & dirty implementation of optional values for the list comparison (since the type could already be null so we don't want to compare by nullability)
  private sealed interface Opt<out T> {
    fun isSome(): Boolean = this is Some
    fun isNone(): Boolean = this is None

    companion object {
      operator fun <T> invoke(value: T?): Opt<T> = value?.let { Some(it) } ?: None
    }

    data class Some<T: Any>(val value: T): Opt<T>
    object None: Opt<Nothing>
  }

//  private def iterable(ai: Iterable[_], bi: Iterable[_]): Option[Diff] = {
//    val fields =
//      ai.map(Option(_))
//        .zipAll(bi.map(Option(_)), None, None)
//        .zipWithIndex
//        .map {
//          // TODO Compare(0, a, b, c) to Compare(a, b, c) should return diff 0
//          case ((Some(a), Some(b)), index) => generic(a, b).withField(index.toString)
//          case ((None, Some(a)), index)    => Some(Diff.MissingLeft(a)).withField(index.toString)
//          case ((Some(a), None), index)    => Some(Diff.MissingRight(a)).withField(index.toString)
//          case _                           => None
//        }
//        .collect { case Some(v) => v }
//        .toList
//    if (fields.isEmpty)
//      None
//    else
//      Some(Diff.Sequence(className(ai.getClass), ListMap.from(fields)))
//  }

  fun KClass<*>.className(): String =
    when {
      MatchAncestry("Set")(this) -> "Set"
      MatchAncestry("List")(this) -> "List"
      MatchAncestry("Map")(this) -> "Map"
      else -> simpleName ?: qualifiedName ?: toString()
    }


  fun KClass<*>.productClassName(): String =
    simpleName ?: "<Unknown-Product>"

  // Very annotying but in kotlin since a * means value that are really Any? i.e. they may or may not be null,
  // we need to introduce a secondary mechanism

  private fun iterable(ai: Iterable<*>, bi: Iterable<*>): Diff? {
    val fields =
      ai.map { Opt(it) }
        .zipAll(bi.map { Opt(it) }, Opt.None, Opt.None)
        .mapIndexed { index, (a, b) ->
          when {
            a is Opt.Some<*> && b is Opt.Some<*> -> generic(a.value, b.value)?.withField(index.toString())
            a is Opt.None && b is Opt.Some<*> -> Diff.MissingLeft(b.value).withField(index.toString())
            a is Opt.Some<*> && b is Opt.None -> Diff.MissingRight(a.value).withField(index.toString())
            else -> null // Neither collection has an element
          }
        }
        .filterNotNull()

    return if (fields.isEmpty())
      null
    else
      Diff.Sequence(ai::class.className(), fields.map { it.first to it.second }.toLinkedMap())
  }

  fun KClass<*>.someRelationWith(other: KClass<*>): Boolean =
    when {
      this == other -> true
      this.isSubclassOf(other) -> true
      other.isSubclassOf(this) -> true
      else -> false
    }

  private fun productNullable(a: Any?, b: Any?): Diff? =
    if (a == null && b == null) matchSuccess(a, b)
    else if (a == null && b != null) Diff.MissingLeft(b)
    else if (a != null && b == null) Diff.MissingRight(a)
    // We've covered all null cases so we can safely use !! here
    else product(a!!, b!!)

  val excludedClassFields = setOf(
    "seen0"
  )

  fun <T> tryOrNull(f: () -> T): T? =
    try {
      f()
    } catch (e: Exception) {
      // Some kind of warning during the comparison that a field could not be dereferenced?
      null
    }

  // TODO Need to fix this upstream in PPrint-Java to be more robust because seeing the following
  //      The parameter name 'seen0' of io.exoquery.xr.XR.CustomQueryRef could not be found within the list of members: [val io.exoquery.xr.XR.CustomQueryRef.customQuery: io.exoquery.xr.XR.CustomQuery, val io.exoquery.xr.XR.CustomQueryRef.loc: io.exoquery.xr.XR.Location, val io.exoquery.xr.XR.CustomQueryRef.productComponents: io.decomat.ProductClass1<io.exoquery.xr.XR.CustomQueryRef, io.exoquery.xr.XR.CustomQuery>, val io.exoquery.xr.XR.CustomQueryRef.type: io.exoquery.xr.XRType, val io.exoquery.xr.XR.CustomQueryRef.productClassValue: io.exoquery.xr.XR.CustomQueryRef, val io.exoquery.xr.XR.CustomQueryRef.productClassValueUntyped: kotlin.Any]
  //      java.lang.IllegalStateException: The parameter name 'seen0' of io.exoquery.xr.XR.CustomQueryRef could not be found within the list of members: [val io.exoquery.xr.XR.CustomQueryRef.customQuery: io.exoquery.xr.XR.CustomQuery, val io.exoquery.xr.XR.CustomQueryRef.loc: io.exoquery.xr.XR.Location, val io.exoquery.xr.XR.CustomQueryRef.productComponents: io.decomat.ProductClass1<io.exoquery.xr.XR.CustomQueryRef, io.exoquery.xr.XR.CustomQuery>, val io.exoquery.xr.XR.CustomQueryRef.type: io.exoquery.xr.XRType, val io.exoquery.xr.XR.CustomQueryRef.productClassValue: io.exoquery.xr.XR.CustomQueryRef, val io.exoquery.xr.XR.CustomQueryRef.productClassValueUntyped: kotlin.Any]
  //      For now just removing the fail on constructor-field-not-found should be fine
  fun KClass<*>.dataClassProperties(): List<KProperty1<Any, Any?>> {
    val constructorParams = this.constructors.first().parameters.map { it.name }.toSet()
    // NOTE: What is the typical number of member-props. Should this be put into a hashmap? Should there be caching?
    val members = this.memberProperties
    val props = constructorParams.mapNotNull { param -> members.find { it.name == param } ?: null }

    return props as List<KProperty1<Any, Any?>>
  }

  private fun product(a: Any, b: Any): Diff? =
    if (!a::class.someRelationWith(b::class))
      Diff.Leaf2(a::class.productClassName(), b::class.productClassName(), a, b)
    else {
      val clsName = a::class.productClassName()

      val aFields = a::class.dataClassProperties().mapNotNull { it.name to tryOrNull { it.getter.call(a) } }
        .filterNot { it.first in skipFields }
      val bFields = b::class.dataClassProperties().mapNotNull { it.name to tryOrNull { it.getter.call(b) } }
        .filterNot { it.first in skipFields }
      if (aFields.sortedBy { it.first } == bFields.sortedBy { it.first })
        null
      else {
        val names = aFields.map { it.first } + bFields.map { it.first }.distinct()
        val aMap = aFields.toMap()
        val bMap = bFields.toMap()
        names
          .map { name ->
            val out = generic(aMap[name], bMap[name])?.withField(name)
            out
          }.filterNotNull()
          .let {
            if (it.isEmpty()) null
            else Diff.Object(clsName, it.map { it.first to it.second }.toLinkedMap())
          }
      }
    }

// Scala:
//object Compare {
//
//  private def product(a: Product, b: Product): Option[Diff] =
//    if (a.getClass != b.getClass) Diff.Leaf.ofClass(a.getClass, b.getClass)(a, b)
//    else {
//      val clsName = className(a.getClass)
//      val names   = a.productElementNames.toList
//      val as      = a.productIterator.toList
//      val bs      = b.productIterator.toList
//      if (names.length != as.length || as.length != bs.length)
//        throw new RuntimeException(
//          s"""Different element lengths found:
//             |========== Names =========
//             |${names.zipWithIndex.map { case (v, i) => s"$i - $v" }.mkString("\n")}
//             |========== AS ========
//             |${as.zipWithIndex.map { case (v, i) => s"$i - $v" }.mkString("\n")}
//             |========== BS ========
//             |${bs.zipWithIndex.map { case (v, i) => s"$i - $v" }.mkString("\n")}""".stripMargin
//        )
//      if (as == bs) None
//      else {
//        val fields =
//          ListMap.from(
//            names
//              .zip(as)
//              .zip(bs)
//              .map { case ((name, a), b) => generic(a, b).withField(name) }
//              .collect { case Some((field, value)) => (field, value) }
//          )
//        Some(Diff.Object(clsName, fields))
//      }
//    }
//

  class MatchAncestry(val name: String) {
    operator fun invoke(value: Any): Boolean =
      value::class.allSuperclasses.find { it.simpleName == name } != null
  }

}

fun <T1, T2> Iterable<T1>.zipAll(other: Iterable<T2>, emptyValue: T1, otherEmptyValue: T2): List<Pair<T1, T2>> {
  val i1 = this.iterator()
  val i2 = other.iterator()
  val output = mutableListOf<Pair<T1, T2>>()
  while (i1.hasNext() || i2.hasNext()) {
    output +=
      Pair(
        if (i1.hasNext()) i1.next() else emptyValue,
        if (i2.hasNext()) i2.next() else otherEmptyValue
      )
  }
  return output
}
