package io.exoquery.plugin.trees

import io.exoquery.plugin.transform.BuilderContext
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGetObjectValue
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetObjectValue
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.typeOf

fun KType.fullPathOfBasic() =
  when(val cls = this.classifier) {
    is KClass<*> -> cls.qualifiedName ?: throw RuntimeException("Qualified name of class $cls was null")
    else -> throw RuntimeException("Invalid list class: $cls")
  }

// TODO this can probably make both objects and types if we check the attributes of the reified type T
//      should look into that
context(BuilderContext) inline fun <reified T> make(vararg args: IrExpression): IrConstructorCall {
  val fullPath = typeOf<T>().fullPathOfBasic()
  return makeClassFromString(fullPath, args.toList())
}

context(BuilderContext) inline fun <reified T> makeWithTypes(types: List<IrType>, args: List<IrExpression>): IrConstructorCall {
  val fullPath = typeOf<T>().fullPathOfBasic()
  return makeClassFromString(fullPath, args.toList(), types.toList())
}

context(BuilderContext) inline fun <reified T> makeObject(): IrGetObjectValue {
  val fullPath = typeOf<T>().fullPathOfBasic()
  return makeObjectFromString(fullPath)
}


context(BuilderContext) fun makeClassFromString(fullPath: String, args: List<IrExpression>, types: List<IrType> = listOf()) =
  pluginCtx.referenceConstructors(ClassId.topLevel(FqName("$fullPath"))).first()
    .let { ctor -> builder.irCall(ctor) }
    .also { ctorCall ->
      args.withIndex().map { (i, arg) ->
        ctorCall.putValueArgument(i, arg)
      }
      types.withIndex().map { (i, arg) ->
        ctorCall.putTypeArgument(i, arg)
      }
    }

context(BuilderContext) fun makeObjectFromString(fullPath: String): IrGetObjectValue {
  val cls = ClassId.topLevel(FqName("$fullPath"))
  val clsSym = pluginCtx.referenceClass(cls) ?: throw RuntimeException("Could not find the reference for the class $cls in the context")
  val tpe = clsSym.owner.defaultType
  return builder.irGetObjectValue(tpe, clsSym)
}