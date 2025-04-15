package io.exoquery.plugin.trees

import io.decomat.Is
import io.exoquery.parseError
import io.exoquery.plugin.dataClassProperties
import io.exoquery.plugin.isDataClass
import io.exoquery.plugin.transform.CX
import io.exoquery.plugin.transform.callDispatch
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irIfNull
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.isNullable
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.irCall

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
    if (
        type.isDataClass() &&
          // Double-check the type parser to make sure the type is not actually supposed to be used as a leaf (I.e. make sure it has no ExoValue or Contextual annotation)
          !Ir.Type.Value[Is()].matchesAny(type)
      ) {
      val cls = type.classOrNull ?: parseError("Expected a class to elaborate, got ${type} which is invalid", parent)

      val safeFields = cls.owner.declarations.filterIsInstance<IrProperty>()
      //error("----------- Safe fields: ${cls.owner.declarations.map { dec ->
      //  ("--------------- ${dec::class.simpleName}, ${dec is IrProperty} --------------\n${dec.dumpKotlinLike()}\n-----------------")
      //}} -----------")

      cls.dataClassProperties().flatMap { (propertyName, propertyType) ->
        val fieldOpt = safeFields.find { it.name.identifier == propertyName }?.backingField
        if (fieldOpt != null) {
          val call =
            with (builder) {
              val getField = irGetField(parent, fieldOpt)
              getField.superQualifierSymbol = cls
              getField.receiver = parent
              getField
            }

          invokeRecurse(currPath + propertyName, callNullSafe(parent, type, propertyType, call), propertyType)
        } else {
          emptyList()
        }
      }
    } else {
      listOf(Path(currPath, parent, type))
    }
  }

  context(CX.Scope, CX.Builder)
  private fun callNullSafe(parent: IrExpression, parentType: IrType, targetType: IrType, call: IrExpression) = run {
    //val call = parent.callDispatch(propertyName).invoke()
    if (parentType.isNullable()) {
      with (builder) {
        irIfNull(targetType, parent, irNull(targetType), call)
      }
    } else {
      call
    }
  }
}
