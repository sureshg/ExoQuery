package io.exoquery.sql

import io.decomat.*
import io.exoquery.xr.XR

object PropertyMatryoshka {

  fun traverse(initial: XR.Property, accum: List<String> = listOf()): Pair<XR, List<String>> =
    with(initial) {
      when {
        // Property(Property(...))
        of is XR.Property ->
          traverse(of, listOf(name) + accum)
        else ->
          of to listOf(name) + accum
      }
    }

    operator fun <AP: Pattern<XR.Expression>, BP: Pattern<List<String>>> get(a: AP, b: BP) =
      customPattern2(a, b) { it: XR.Expression ->
        when (it) {
          is XR.Property -> traverse(it).let { (xr, path) -> Components2(xr, path) }
          else -> null
        }
      }
}

fun isPropertyOrCore(it: XR.Expression) =
  it is XR.Property || it is XR.Ident || it is XR.Infix || it is XR.Const

fun PropertyOrCore() =
  Is<XR.Expression> {
    val check = it is XR.Property || it is XR.Ident || it is XR.Infix || it is XR.Const
    check
  }

fun Core() = Is<XR.Expression> { it is XR.Ident || it is XR.Infix || it is XR.Const }
