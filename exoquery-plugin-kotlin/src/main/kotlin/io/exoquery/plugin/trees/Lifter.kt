package io.exoquery.plugin.trees

import io.exoquery.*
import io.exoquery.xr.XR.*
import io.exoquery.plugin.transform.BuilderContext
import io.exoquery.xr.*
import io.exoquery.xr.XR.Query

import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetObjectValue
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.isVararg
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import kotlin.reflect.typeOf

class Lifter(val builderCtx: BuilderContext) {
  val irBuilder = builderCtx.builder
  val context = builderCtx.pluginCtx


  inline fun <reified T> make(vararg args: IrExpression): IrConstructorCall = with(builderCtx) { io.exoquery.plugin.trees.make<T>(*args) }
  inline fun <reified T> makeObject(): IrGetObjectValue = with(builderCtx) { io.exoquery.plugin.trees.makeObject<T>() }
  fun makeClassFromString(fullPath: String, args: List<IrExpression>, types: List<IrType> = listOf()) =
    with(builderCtx) { io.exoquery.plugin.trees.makeClassFromString(fullPath, args, types) }
  fun makeObjectFromString(fullPath: String): IrGetObjectValue =
    with(builderCtx) { io.exoquery.plugin.trees.makeObjectFromString(fullPath) }
  inline fun <reified T> makeWithTypes(types: List<IrType>, args: List<IrExpression>): IrConstructorCall =
    with(builderCtx) { io.exoquery.plugin.trees.makeWithTypes<T>(types, args) }

  fun Boolean.lift(): IrExpression = irBuilder.irBoolean(this)
  fun Byte.lift(): IrExpression = IrConstImpl.byte(irBuilder.startOffset, irBuilder.endOffset, context.irBuiltIns.byteType, this)
  fun Char.lift(): IrExpression = irBuilder.irChar(this)
  fun Double.lift(): IrExpression = IrConstImpl.double(irBuilder.startOffset, irBuilder.endOffset, context.irBuiltIns.doubleType, this)
  fun Float.lift(): IrExpression = IrConstImpl.float(irBuilder.startOffset, irBuilder.endOffset, context.irBuiltIns.floatType, this)
  fun Int.lift(): IrExpression = irBuilder.irInt(this)
  fun Long.lift(): IrExpression = irBuilder.irLong(this)
  fun Short.lift(): IrExpression = IrConstImpl.short(irBuilder.startOffset, irBuilder.endOffset, context.irBuiltIns.shortType, this)
  fun String.lift(): IrExpression = irBuilder.irString(this)

  val listOfRef =
    context.referenceFunctions(CallableId(FqName("kotlin.collections"), Name.identifier("listOf") ))
      // Get the 1st variadic instance of listOf (note that some variations have zero args so need to do firstOrNull)
      .first { it.owner.valueParameters.firstOrNull()?.isVararg ?: false }

  inline fun <reified T> List<T>.lift(elementLifter: (T) -> IrExpression): IrExpression {
    val elementType = typeOf<T>()
    if (elementType == typeOf<IrExpression>()) {
      // the input arg is already an expression. At this point it's more of a mapper than a lifter
      // (because the user could still call a map function and modify the expression in some way)
      // so at that point just do the map and call it
      val exprs = this.map(elementLifter)
      return exprs.liftExpr<Any>()
    } else {
      // We go throught the bruhaha of finding the constructor for the element type because we need to know it in order to create the varags list i.e. the `...` argument to `listOf(...)`
      // otherwise we could just call the list constructor and ignore the element type and rely on the compiler's type inference
      val fullPath = elementType.fullPathOfBasic()
      val classId = ClassId.topLevel(FqName(fullPath))
      val expressionType = context.referenceConstructors(classId).firstOrNull()?.owner?.returnType ?: throw IllegalStateException("Cannot find a constructor for: ${classId} for the element type: ${elementType}")
      val expressions = this.map { elementLifter(it) }
      val variadics = irBuilder.irVararg(expressionType, expressions)
      val listOfCall = irBuilder.irCall(listOfRef).apply { putValueArgument(0, variadics) }
      return listOfCall
    }
  }

  inline fun <reified T> List<IrExpression>.liftExpr(): IrExpression {
    val elementType = typeOf<T>()
    val fullPath = elementType.fullPathOfBasic()
    val classId = ClassId.topLevel(FqName(fullPath))
    val expressionType = context.referenceConstructors(classId).first().owner.returnType
    val variadics = irBuilder.irVararg(expressionType, this)
    //builderCtx.logger.error("--------------- Expression Type -------------: ${expressionType.dumpKotlinLike()}")
    val listOfCall = irBuilder.irCall(listOfRef, context.symbols.list.typeWith(expressionType)).apply {
      putTypeArgument(0, expressionType)
      putValueArgument(0, variadics)
    }
    //builderCtx.logger.error("--------------- List Expression Type -------------: ${listOfCall.type.dumpKotlinLike()}\n=== ${builderCtx.currentFile.path} ===")
    return listOfCall
  }

  fun List<IrExpression>.liftExprTyped(elementType: IrType): IrExpression {
    val variadics = irBuilder.irVararg(elementType, this)
    val listOfCall = irBuilder.irCall(listOfRef, context.symbols.list.typeWith(elementType)).apply {
      putTypeArgument(0, elementType)
      putValueArgument(0, variadics)
    }
    return listOfCall
  }

  fun <A, B> Pair<A, B>.lift(aLifter: (A) -> IrExpression, bLifter: (B) -> IrExpression): IrExpression =
    make<Pair<A, B>>(aLifter(this.first), bLifter(this.second))

  fun BID.lift(): IrExpression =
    make<BID>(irBuilder.irString(this.component1()))
}

// Some top-level lift functions to use outside of the lifter
