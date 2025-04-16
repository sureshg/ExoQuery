package io.exoquery.plugin.trees

import io.decomat.Is
import io.exoquery.parseError
import io.exoquery.plugin.dataClassProperties
import io.exoquery.plugin.isDataClass
import io.exoquery.plugin.printing.dumpSimple
import io.exoquery.plugin.transform.CX
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irIfNull
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.isInt
import org.jetbrains.kotlin.ir.types.isNullable
import org.jetbrains.kotlin.ir.types.isString
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.ir.util.getPropertyGetter
import org.jetbrains.kotlin.ir.util.shallowCopy

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
      val clsOwner = cls.owner


      //error("----------- Safe fields: ${cls.dataClassProperties().map { (name, tpe) ->
      //  ("--------------- ${name} --------------\n${cls.getPropertyGetter(name)}\n-----------------")
      //}} -----------")

      cls.dataClassProperties().flatMap { (propertyName, propertyType) ->
        val property = clsOwner.declarations
          .filterIsInstance<IrProperty>()
          .firstOrNull { it.name.asString() == propertyName }
          ?: error("Property $propertyName not found")

        val getterSymbol = property?.getter?.symbol ?: error("Getter for $propertyName not found")

        val backingFieldSymbol = property.backingField?.symbol
        if (backingFieldSymbol != null && !backingFieldSymbol.isBound) {
          error("Backing field for $propertyName is unbound!")
        }

        val explicitReceiver = parent
        if (getterSymbol != null) {
          val call =
            with (builder) {
              irCall(getterSymbol).apply {
                dispatchReceiver = explicitReceiver
              }
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
