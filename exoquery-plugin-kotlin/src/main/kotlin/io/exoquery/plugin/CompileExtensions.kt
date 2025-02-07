package io.exoquery.plugin

import io.decomat.*
import io.decomat.fail.fail
import io.exoquery.annotation.*
import io.exoquery.plugin.transform.BuilderContext
import io.exoquery.plugin.transform.Caller
import io.exoquery.plugin.transform.LocateableContext
import io.exoquery.plugin.transform.LoggableContext
import io.exoquery.plugin.trees.Ir
import io.exoquery.plugin.trees.LocationContext
import io.exoquery.plugin.trees.ParserContext
import io.exoquery.xr.XR
import org.jetbrains.kotlin.backend.jvm.ir.getKtFile
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocationWithRange
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.fir.backend.FirMetadataSource
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrFileEntry
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.IrAnnotationContainer
import org.jetbrains.kotlin.ir.declarations.IrDeclarationWithName
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrSymbolOwner
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.isPropertyAccessor
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrFieldSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import kotlin.reflect.KClass

val KClass<*>.qualifiedNameForce get(): String =
  if (this.qualifiedName == null) fail("Qualified name of the class ${this} was null")
  else this.qualifiedName!!

val KClass<*>.fqNameForce get() =
  FqName(this.qualifiedNameForce)

sealed interface MethodType {
  data class Getter(val sym: IrSimpleFunctionSymbol): MethodType
  data class Method(val sym: IrSimpleFunctionSymbol): MethodType
}

fun IrType.findMethodOrFail(methodName: String): MethodType = run {
  val cls =
    (this.classOrNull ?: error("Cannot locate the method ${methodName} from the type: ${this.dumpKotlinLike()} type is not a class."))

  fun printError(addition: String = ""): Nothing =
    error(
     """|
        |Cannot locate the method `${methodName}` from the type: `${this.dumpKotlinLike()}` because the method does not exist.${addition}
        |-------------- Available methods --------------
        |${cls.functions.joinToString("\n") { it.safeName }}
        |-------------- Available properties --------------
        |${cls.dataClassProperties().mapNotNull { cls.getPropertyGetter(it.first)?.safeName }.joinToString("\n")}
        |""".trimMargin())

  cls.functions.find { it.safeName == methodName }?.let { MethodType.Method(it) }
    ?: run {
      try {
        cls.getPropertyGetter(methodName)?.let { MethodType.Getter(it) }
      } catch (e: AssertionError) {
        printError(" Also, attempting getPropertyGetter caused an assert-error.")
      }
    }
    ?: printError()
}

// WARNING assuming (for now) that the extension methods are in the same package as the Class they're being called from.
// can relax this assumption later by adding an optional package-field to ReplacementMethodToCall and propagating it here
// TODO Need to filter by reciever type i.e. what if there are multiple extension functions named the same thing
context(BuilderContext) fun IrType.findExtensionMethodOrFail(methodName: String) = run {
  (this
    .classOrNull ?: error("Cannot locate the method ${methodName} from the type: ${this.dumpKotlinLike()} type is not a class."))
    .let { classSym ->
      pluginCtx.referenceFunctions(CallableId(FqName(classSym.owner.packageFqName.toString()), Name.identifier(methodName))).firstOrNull()?.let { MethodType.Method(it) }
        ?: error("Cannot locate the extension method ${classSym.owner.packageFqName.toString()}.${methodName} from the type: ${this.dumpKotlinLike()} because the method does not exist.")
    }
}

fun IrClassSymbol.isDataClass() = this.owner.isData

fun IrClassSymbol.dataClassProperties() =
  if (this.isDataClass()) {
    // NOTE: Does not support data-classes with multiple constructors.
    // Constructor params are in the right order. The properties of the class are not.
    val constructorParams = this.constructors.firstOrNull()?.owner?.valueParameters ?: setOf()
    //this.owner.properties
    //  .filter { constructorParams.contains(it.name) && it.getter != null }
    //  .map { it.name.toString() to it.getter!!.returnType }
    constructorParams.map { param -> param.name.asString() to param.type }
  }
  else listOf()


val IrSimpleFunctionSymbol.fullName get() = this.owner.kotlinFqName.asString()

val IrCall.symName get() = this.symbol.safeName

fun IrGetValue.isFunctionParam() =
  this.symbol.owner is IrValueParameter && this.symbol.owner.parent is IrSimpleFunction

fun IrGetValue.ownerFunction() =
  (this.symbol.owner as? IrValueParameter)?.let { it.parent as? IrSimpleFunction }

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

fun CompilerMessageSourceLocation.show() =
  "${path}:${line}:${column}"

// TODO change to LocationContainingContext
context(LocateableContext) fun IrElement.location(): CompilerMessageSourceLocation =
  this.location(currentFile.fileEntry)

fun IrFile.location(): CompilerMessageSourceLocation =
  this.location(this.fileEntry)

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

inline fun <reified T> classIdOf(): ClassId {
  val className = T::class.qualifiedNameForce
  return ClassId.topLevel(FqName(className))
}

inline fun <reified T> fqNameOf(): FqName {
  val className = T::class.qualifiedNameForce
  return FqName(className)
}

// IrFile is both a IrSymbolOwner and a IrAnnotationContainer so
// have a override specifically for. Otherwise would need to use a @this for it
inline fun <reified T> IrFile.hasAnnotation() =
  (this as? IrAnnotationContainer)?.hasAnnotation<T>() ?: false

inline fun <reified T> IrFile.getAnnotation() =
  (this as? IrAnnotationContainer)?.let { it.annotations.find { ctor -> ctor.type.isClass<T>() } }

inline fun <reified T> IrSymbolOwner.hasAnnotation() =
  (this as? IrAnnotationContainer)?.hasAnnotation<T>() ?: false

inline fun <reified T> IrAnnotationContainer.hasAnnotation() =
  this.annotations.any { it.type.classFqName == fqNameOf<T>() }

inline fun <reified T> IrType.isClass(): Boolean {
  // NOTE memoize these things for performance?
  val className = T::class.qualifiedNameForce
  return className == this.classFqName.toString() || this.superTypes().any { it.classFqName.toString() == className }
}

context(LoggableContext) fun IrExpression.varargValues(): List<IrExpression> =
  (this as? IrVararg ?: run{
    logger.warn("[ExoQuery-WARN] Expected the argument to be an IrVararg but it was not: ${this.dumpKotlinLike()}"); null
    null
  })?.elements?.mapNotNull {
    it as? IrExpression ?: run{
      logger.warn("[ExoQuery-WARN] Expected the argument to be an IrExpression but it was not: ${it.dumpKotlinLike()}"); null
    }
  } ?: emptyList()

inline fun <reified T> IrCall.reciverIs() =
  this.dispatchReceiver?.isClass<T>() ?: false

inline fun <reified T> IrCall.reciverIs(methodName: String) =
  this.dispatchReceiver?.isClass<T>() ?: false && this.symbol.safeName == methodName

data class ReplacementMethodToCall(val methodToCall: String, val callerType: ChangeReciever = ChangeReciever.DoNothing) {
  companion object {
    fun from(call: IrConstructorCall) =
      call.getValueArgument(0)?.let { firstArg ->
        if (firstArg is IrConst && firstArg.kind == IrConstKind.String) {
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


fun IrCall.caller() =
  this.extensionReceiver?.let {
    Caller.Extension(it)
  } ?:
  this.dispatchReceiver?.let {
    Caller.Dispatch(it)
  }

// Best-effort to get the source of the file
context(LocateableContext) fun IrElement.source(): String? = run {
  val range = TextRange(this.startOffset, this.endOffset)

  fun getFromFirSource() =
    (currentFile.metadata as? FirMetadataSource.File)
      ?.fir
      ?.source
      ?.getElementTextInContextForDebug()
      ?.let { range.substring(it) }

  fun getFromKtFile() =
    currentFile.getKtFile()?.let { ktFile ->
      ktFile.textRange.cutOut(range).let { cutOut ->
        ktFile.text.let { textValue ->
          cutOut.substring(textValue)
        }
      }
    }

  getFromFirSource() ?: getFromKtFile()
}
