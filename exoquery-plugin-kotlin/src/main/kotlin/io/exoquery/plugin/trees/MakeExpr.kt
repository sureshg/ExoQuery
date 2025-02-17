package io.exoquery.plugin.trees

import io.exoquery.liftingError
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

fun KType.fullPathOfBasic(): ClassId =
  when(val cls = this.classifier) {
    is KClass<*> -> {
      // foo.bar in foo.bar.Baz.Blin
      val packageName = cls.java.packageName
      // the full path foo.bar.Baz.Blin
      val fullPath = cls.qualifiedName ?: liftingError("Could not get qualified name of class $cls")
      if (!fullPath.startsWith(packageName))
        liftingError("Qualified name of class $fullPath did not start with package name $packageName")

      // the Baz.Blin part
      val className = fullPath.replace(packageName, "").dropWhile { it == '.' } // after we replaced foo.bar with "" there's still a leading "." that wee need to remove
      ClassId(FqName(packageName), FqName(className), false)
    }
    else -> throw RuntimeException("Invalid list class: $cls")
  }

// TODO this can probably make both objects and types if we check the attributes of the reified type T
//      should look into that
context(BuilderContext) inline fun <reified T> make(vararg args: IrExpression): IrConstructorCall {
  val fullPath = typeOf<T>().fullPathOfBasic()
  return makeClassFromId(fullPath, args.toList())
}

context(BuilderContext) inline fun <reified T> makeWithTypes(types: List<IrType>, args: List<IrExpression>): IrConstructorCall {
  val fullPath = typeOf<T>().fullPathOfBasic()
  return makeClassFromId(fullPath, args.toList(), types.toList())
}

context(BuilderContext) inline fun <reified T> makeObject(): IrGetObjectValue {
  val fullPath = typeOf<T>().fullPathOfBasic()
  return makeObjectFromId(fullPath)
}


context(BuilderContext) fun makeClassFromString(fullPath: String, args: List<IrExpression>, types: List<IrType> = listOf(), overrideType: IrType? = null) =
  makeClassFromId(ClassId.topLevel(FqName(fullPath)), args, types, overrideType)

context(BuilderContext) fun makeClassFromId(fullPath: ClassId, args: List<IrExpression>, types: List<IrType> = listOf(), overrideType: IrType? = null) =
  pluginCtx.referenceConstructors(fullPath).first()
    .let { ctor ->
      overrideType?.let { builder.irCall(ctor, it) } ?: builder.irCall(ctor)
    }
    .also { ctorCall ->
      args.withIndex().map { (i, arg) ->
        ctorCall.putValueArgument(i, arg)
      }
      types.withIndex().map { (i, arg) ->
        ctorCall.putTypeArgument(i, arg)
      }
    }

context(BuilderContext) fun makeObjectFromId(id: ClassId): IrGetObjectValue {
  val clsSym = pluginCtx.referenceClass(id) ?: throw RuntimeException("Could not find the reference for a class in the context: $id")
  val tpe = clsSym.owner.defaultType
  return builder.irGetObjectValue(tpe, clsSym)
}
