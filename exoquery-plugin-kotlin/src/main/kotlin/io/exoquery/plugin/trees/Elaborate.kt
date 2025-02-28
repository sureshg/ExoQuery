package io.exoquery.plugin.trees

import io.exoquery.parseError
import io.exoquery.plugin.dataClassProperties
import io.exoquery.plugin.isDataClass
import io.exoquery.plugin.transform.CX
import io.exoquery.plugin.transform.callDispatch
import org.jetbrains.kotlin.ir.builders.irIfNull
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.isNullable

object Elaborate {
  data class Path(val path: List<String>, val invocation: IrExpression, val type: IrType)

  context(CX.Scope, CX.Builder)
  fun invoke(expr: IrExpression) = run {
    if (!expr.type.isDataClass())
      parseError("Expected a data class to elaborate, got ${expr.type}", expr)

    invokeRecurse(emptyList(), expr, expr.type)
  }

  // data class Person(name, age), and an identifier p:Person ->
  //  ([name], p.name), ([age], p.age)
  // data class Person(name: Name(first, last), age), and an identifier p:Person ->
  //  ([name, first], p.name.first), ([name, last], p.name.last), ([age], p.age)
  // However, if name is nullable e.g:
  // data class Person(name: Name(first, last)?, age), and an identifier p:Person ->
  //  ([name, first], if (p.name == null) null else p.first), ([name, last], if (p.name == null) null else p.last), ([age], p.age)
  // Same pattern for futher nested nullables
  context(CX.Scope, CX.Builder)
  private fun invokeRecurse(currPath: List<String>, parent: IrExpression, type: IrType): List<Path> = run {
    if (type.isDataClass()) {
      type.classOrNull!!.dataClassProperties().flatMap { (propertyName, propertyType) ->
        invokeRecurse(currPath + propertyName, callNullSafe(parent, type, propertyType, propertyName), propertyType)
      }
    } else {
      listOf(Path(currPath, parent, type))
    }
  }

  context(CX.Scope, CX.Builder)
  private fun callNullSafe(parent: IrExpression, parentType: IrType, targetType: IrType, propertyName: String) = run {
    val call = parent.callDispatch(propertyName).invoke()
    if (parentType.isNullable()) {
      with (builder) {
        irIfNull(targetType, parent, irNull(targetType), call)
      }
    } else {
      call
    }
  }
}
