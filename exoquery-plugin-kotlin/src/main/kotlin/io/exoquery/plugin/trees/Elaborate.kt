package io.exoquery.plugin.trees

import io.decomat.Is
import io.exoquery.annotation.ExoField
import io.exoquery.parseError
import io.exoquery.plugin.dataClassProperties
import io.exoquery.plugin.firstConstStringOrNull
import io.exoquery.plugin.getAnnotation
import io.exoquery.plugin.getAnnotationArgs
import io.exoquery.plugin.isDataClass
import io.exoquery.plugin.transform.CX
import io.exoquery.xr.XRType
import kotlinx.serialization.Serializable
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irIfNull
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.expressions.IrClassReference
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.isNullable

object Elaborate {
  data class Path(val path: List<String>, val invocation: IrExpression, val type: IrType, val xrType: XRType, val knownSerializer: IrClassReference?) {
    override fun toString(): String = path.joinToString(".")
  }

  context(CX.Scope, CX.Builder)
  fun invoke(expr: IrExpression, rootType: XRType.Product) = run {
    if (!expr.type.isDataClass())
      parseError("Expected a data class to elaborate, got ${expr.type}", expr)

    invokeRecurse(emptyList(), expr, expr.type, rootType, null)
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
  private fun invokeRecurse(currPath: List<String>, parent: IrExpression, type: IrType, currentXrType: XRType, knownFieldSerializer: IrClassReference?): List<Path> = run {
    if (currentXrType.isProduct()) {
      val cls = type.classOrNull ?: parseError("Expected a class to elaborate, got ${type} which is invalid", parent)
      val clsOwner = cls.owner
      // explicitly mention it since we've already checked above
      currentXrType as XRType.Product


      //error("----------- Safe fields: ${cls.dataClassProperties().map { (name, tpe) ->
      //  ("--------------- ${name} --------------\n${cls.getPropertyGetter(name)}\n-----------------")
      //}} -----------")

      val props = clsOwner.declarations.filterIsInstance<IrProperty>()

      cls.dataClassProperties().flatMap { (propertyName, propertyType) ->
        val property = props.firstOrNull { it.name.asString() == propertyName } ?: error("Property $propertyName not found")

        // To get the @ExoField or @SerialName annotation we need to get the getter in case this was overridden
        val sqlPropertyName =
          property.getAnnotationArgs<ExoField>().firstConstStringOrNull()
            ?: property.getAnnotationArgs<kotlinx.serialization.SerialName>().firstConstStringOrNull()
            ?: propertyName

        val knownFieldSerializer =
          (property.getAnnotationArgs<kotlinx.serialization.Serializable>().firstOrNull()
              ?: propertyType.getAnnotationArgs<kotlinx.serialization.Serializable>().firstOrNull()
              ?: propertyType.classOrNull?.owner?.getAnnotationArgs<kotlinx.serialization.Serializable>()?.firstOrNull()
          ).let { it as? IrClassReference }

        // Need to call the getting-symbol in order to get the field value, also make sure the backing field is bound
        val getterSymbol = property?.getter?.symbol ?: error("Getter for $propertyName not found")
        val backingFieldSymbol = property.backingField?.symbol
        if (backingFieldSymbol != null && !backingFieldSymbol.isBound) {
          error("Backing field for $propertyName is unbound!")
        }

        val nextXrType = currentXrType.fields.find { it.first == sqlPropertyName }?.second
          ?: parseError("Field $propertyName not found in ${currentXrType.name}", parent)

        val explicitReceiver = parent
        if (getterSymbol != null) {
          val call =
            with(builder) {
              irCall(getterSymbol).apply {
                dispatchReceiver = explicitReceiver
              }
            }
          invokeRecurse(currPath + sqlPropertyName, callNullSafe(parent, type, propertyType, call), propertyType, nextXrType, knownFieldSerializer)
        } else {
          emptyList()
        }
      }
    } else {
      listOf(Path(currPath, parent, type, currentXrType, knownFieldSerializer))
    }
  }

  context(CX.Scope, CX.Builder)
  private fun callNullSafe(parent: IrExpression, parentType: IrType, targetType: IrType, call: IrExpression) = run {
    //val call = parent.callDispatch(propertyName).invoke()
    if (parentType.isNullable()) {
      with(builder) {
        irIfNull(targetType, parent, irNull(targetType), call)
      }
    } else {
      call
    }
  }
}
