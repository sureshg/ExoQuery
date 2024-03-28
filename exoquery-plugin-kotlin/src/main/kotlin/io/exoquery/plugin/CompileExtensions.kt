package io.exoquery.plugin

import io.decomat.*
import io.decomat.fail.fail
import io.exoquery.annotation.*
import io.exoquery.plugin.transform.BuilderContext
import io.exoquery.plugin.transform.Caller
import io.exoquery.plugin.trees.Ir
import io.exoquery.plugin.trees.ParserContext
import io.exoquery.xr.XR
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocationWithRange
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrFileEntry
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.IrDeclarationWithName
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.isPropertyAccessor
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import kotlin.reflect.KClass

val KClass<*>.qualifiedNameForce get(): String =
  if (this.qualifiedName == null) fail("Qualified name of the class ${this} was null")
  else this.qualifiedName!!

val KClass<*>.fqNameForce get() =
  FqName(this.qualifiedNameForce)

fun IrType.findMethodOrFail(methodName: String) = run {
  (this
    .classOrNull ?: error("Cannot locate the method ${methodName} from the type: ${this.dumpKotlinLike()} type is not a class."))
    .functions
    .find { it.safeName == methodName } ?: error("Cannot locate the method ${methodName} from the type: ${this.dumpKotlinLike()} because the method does not exist.")
}

// WARNING assuming (for now) that the extension methods are in the same package as the Class they're being called from.
// can relax this assumption later by adding an optional package-field to ReplacementMethodToCall and propagating it here
context(BuilderContext) fun IrType.findExtensionMethodOrFail(methodName: String) = run {
  (this
    .classOrNull ?: error("Cannot locate the method ${methodName} from the type: ${this.dumpKotlinLike()} type is not a class."))
    .let { classSym ->
      pluginCtx.referenceFunctions(CallableId(FqName(classSym.owner.packageFqName.toString()), Name.identifier(methodName))).firstOrNull()
        ?: error("Cannot locate the extension method ${classSym.owner.packageFqName.toString()}.${methodName} from the type: ${this.dumpKotlinLike()} because the method does not exist.")
    }
}

fun IrClassSymbol.isDataClass() = this.owner.isData

fun IrClassSymbol.dataClassProperties() =
  if (this.isDataClass()) {
    val constructorParams = this.constructors.firstOrNull()?.owner?.valueParameters?.map { it.name }?.toSet() ?: setOf()
    this.owner.properties
      .filter { constructorParams.contains(it.name) && it.getter != null }
      .map { it.name.toString() to it.getter!!.returnType }
  }
  else sequenceOf()

val IrSymbol.safeName   get() =
  (if (owner is IrFunction && (owner as IrFunction).isPropertyAccessor) {
    (owner as IrFunction).name.asStringStripSpecialMarkers().removePrefix("get-")
  } else if (isBound) {
    (owner as? IrDeclarationWithName)?.name?.asString() ?: "<???>"
  } else {
    "<???>"
  }).replace("$", "")

fun IrElement.location(fileEntry: IrFileEntry): CompilerMessageSourceLocation {
  val irElement = this
  val sourceRangeInfo = fileEntry.getSourceRangeInfo(
    beginOffset = irElement.startOffset ?: UNDEFINED_OFFSET,
    endOffset = irElement.endOffset ?: UNDEFINED_OFFSET
  )
  val messageWithRange = CompilerMessageLocationWithRange.create(
    path = sourceRangeInfo.filePath,
    lineStart = sourceRangeInfo.startLineNumber + 1,
    columnStart = sourceRangeInfo.startColumnNumber + 1,
    lineEnd = sourceRangeInfo.endLineNumber + 1,
    columnEnd = sourceRangeInfo.endColumnNumber + 1,
    lineContent = null
  )!!
  return messageWithRange
}

context(ParserContext) fun IrElement.location(): CompilerMessageSourceLocation =
  this.location(currentFile.fileEntry)

context(ParserContext) fun IrElement.locationXR(): XR.Location =
  this.location(currentFile.fileEntry).toLocationXR()

context(BuilderContext) fun IrElement.buildLocation(): CompilerMessageSourceLocation =
  this.location(currentFile.fileEntry)

context(BuilderContext) fun IrElement.buildLocationXR(): XR.Location =
  this.location(currentFile.fileEntry).toLocationXR()

fun CompilerMessageSourceLocation.toLocationXR(): XR.Location =
  XR.Location.File(path, line, column)


inline fun <reified T> IrExpression.isClass(): Boolean {
  val className = T::class.qualifiedNameForce
  return className == this.type.classFqName.toString() || type.superTypes().any { it.classFqName.toString() == className }
}

inline fun <reified T> IrType.isClass(): Boolean {
  val className = T::class.qualifiedNameForce
  return className == this.classFqName.toString() || this.superTypes().any { it.classFqName.toString() == className }
}

inline fun <reified T> IrCall.reciverIs() =
  this.dispatchReceiver?.isClass<T>() ?: false

inline fun <reified T> IrCall.reciverIs(methodName: String) =
  this.dispatchReceiver?.isClass<T>() ?: false && this.symbol.safeName == methodName

data class ReplacementMethodToCall(val methodToCall: String, val callerType: ChangeReciever = ChangeReciever.DoNothing) {
  companion object {
    fun from(call: IrConstructorCall) =
      call.getValueArgument(0)?.let { firstArg ->
        if (firstArg is IrConst<*> && firstArg.kind == IrConstKind.String) {
          val secondArg: ChangeReciever =
            call.getValueArgument(1)?.let { secondArg ->
              secondArg.match(
                case(Ir.GetEnumValue[Is()]).then { it.safeName }
              )
            }?.let { secondArgValue ->
              ChangeReciever.valueOf(secondArgValue)
            }
            ?: ChangeReciever.DoNothing

          ReplacementMethodToCall(firstArg.value as String, secondArg)
        } else
          null
      }
  }
}

fun IrCall.markedQueryClauseDirectMethod() =
  this.symbol.owner.annotations.findAnnotation(QueryClauseDirectMethod::class.fqNameForce)?.let { ReplacementMethodToCall.from(it) }

fun IrCall.markedQueryClauseAliasedMethod() =
  this.symbol.owner.annotations.findAnnotation(QueryClauseAliasedMethod::class.fqNameForce)?.let { ReplacementMethodToCall.from(it) }

fun IrCall.markedQueryClauseUnitBind() =
  this.symbol.owner.annotations.findAnnotation(QueryClauseUnitBind::class.fqNameForce)?.let { ReplacementMethodToCall.from(it) }

fun IrCall.markedMethodProducingXR() =
  this.symbol.owner.annotations.findAnnotation(MethodProducingXR::class.fqNameForce)?.let { ReplacementMethodToCall.from(it) }

fun IrCall.markedLambdaMethodProducingXR() =
  this.symbol.owner.annotations.findAnnotation(LambdaMethodProducingXR::class.fqNameForce)?.let { ReplacementMethodToCall.from(it) }

fun IrCall.isExoMethodAnnotated(name: String) =
  this.symbol.owner.annotations.findAnnotation(ExoMethodName::class.fqNameForce)
    ?.let { it.getValueArgument(0) }
    ?.let { it is IrConst<*> && it.kind == IrConstKind.String && it.value as kotlin.String == name }
    ?: false

fun IrCall.isNotExoMethodAnnotated() =
  !this.symbol.owner.annotations.hasAnnotation(ExoMethodName::class.fqNameForce)

fun IrValueParameter.isAnnotatedParseXR() =
  this.annotations.hasAnnotation(ParseXR::class.fqNameForce)

fun IrCall.caller() =
  this.extensionReceiver?.let {
    val changeRecieverCall =

    Caller.ExtensionReceiver(it)
  } ?:
  this.dispatchReceiver?.let {
    Caller.DispatchReceiver(it)
  }
