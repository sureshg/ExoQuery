package io.exoquery.plugin.logging

import io.exoquery.annotation.CapturedFunction
import io.exoquery.plugin.dataClassProperties
import io.exoquery.plugin.printing.dumpSimple
import io.exoquery.plugin.source
import io.exoquery.plugin.transform.LocateableContext
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.*

object Messages {

fun VariableComingFromNonCapturedFunction(funName: String) =
"""
It appears that this expression is an argument coming from a function call. In this case, annotate the function with @CapturedDynamic
to mark it as a dynamic query. If the whole function `${funName}` just returns a SqlQuery and does nothing
else, annotate it as @CapturedFunction and you can then use it to build compile-time functions.
""".trimIndent()


fun TypeParseErrorMsg(msg: String) =
"""
$msg
If this is a custom type defined on a data-class e.g. `data class Customer(lastTransacted: MyCustomDate)` make sure to either:

1) Annotate the type (or field) with @kotlinx.serialization.Contextual e.g. `data class Customer(lastTransacted: @Contextual MyCustomDate)`.
   When decoding the `Customer` instance you will need to give it a custom encoder for MyCustomDate.
   
2) Annotate the type with @ExoValue e.g. `data class Customer(lastTransacted: @ExoValue MyCustomDate)`. This will treat the type as a value type
   in ExoQuery independently of how it is serialized i.e. there could be other Serialization-related annotations on the field for example:
   `@Serializable(with=Something::class) class MyCustomDate(...); data class Customer(lastTransacted: @ExoValue MyCustomDate)`.
""".trimIndent()

context(LocateableContext)
fun CapturedFunctionFormWrong(msg: String) =
"""
$msg

The form of the function annotated with @CapturedFunction is incorrect. It must be a function with a 
single output expression that returns a SqlQuery<T> instance. For example:

@CapturedFunction
fun myFunction(): SqlQuery<Int> = capture { Table<Person>().map { it.age } }

@CapturedFunction
fun myFunction(): SqlQuery<Int> = select { 
  val p = from(Table<Person>())
  p.age
}
""".trimIndent()


  fun ParserMessage(ir: IrExpression?, parsedCode: String?) =
"""
================ Parsed As: ================
$parsedCode
================ Interpreted IR: ================
${ir?.dumpKotlinLike()}
================= Raw IR: ========================
${ir?.dumpSimple()}
""".trimIndent()


  fun PrintingMessage(ir: IrExpression?) =
"""
================ Interpreated IR: ================
${ir?.dumpKotlinLike()}
================= Raw IR: ========================
${ir?.dumpSimple()}
""".trimIndent()

  fun PrintingMessageMulti(ir: List<IrElement>?, additionalHeading: String = ""): String {
    fun writeOutput(ir: IrElement?): String =
      when(ir) {
        is IrReturn -> {
          val tpe = ir.value.type
          val additionalData: String =
            if (true) {
              (tpe.classOrNull?.dataClassProperties() ?: listOf())
                .map { (name, value) -> "$name: ${value.dumpKotlinLike()}" }
                .joinToString(", ", "[", "]")
            } else {
              "$tpe is not a KClass"
            }
          "(Return Value): " + tpe.dumpKotlinLike() + " - " + additionalData
        }
        is IrExpression -> ir.type.dumpKotlinLike()
        else -> "No Type"
      }

val additionalPrint = if (additionalHeading.isNotEmpty()) " ($additionalHeading)" else ""

return """
***
***************************************** Print Source *****************************************
***
================ Kotlin-Like:${additionalPrint} ================
${ir?.withIndex()?.map { (i, it) -> "($i) " + it.dumpKotlinLike() }?.joinToString("\n")}
================= IR: ========================
${ir?.withIndex()?.map { (i, it) -> "($i) " +  it.dumpSimple() }?.joinToString("\n")}
================= Output Type: ========================
${ir?.withIndex()?.map { (i, it) -> "($i) " + writeOutput(it) }?.joinToString("\n")}
""".trimIndent()
}

  fun PrintingMessageSingle(ir: IrElement, additionalHeading: String = ""): String {
    fun writeOutput(ir: IrElement): String =
      when(ir) {
        is IrReturn -> {
          val tpe = ir.value.type
          val additionalData: String =
            if (true) {
              (tpe.classOrNull?.dataClassProperties() ?: listOf())
                .map { (name, value) -> "$name: ${value.dumpKotlinLike()}" }
                .joinToString(", ", "[", "]")
            } else {
              "$tpe is not a KClass"
            }
          "(Return Value): " + tpe.dumpKotlinLike() + " - " + additionalData
        }
        is IrExpression -> ir.type.dumpKotlinLike()
        else -> "No Type"
      }

val additionalPrint = if (additionalHeading.isNotEmpty()) " ($additionalHeading)" else ""

return """
================ Kotlin-Like:${additionalPrint} ================
${ir.dumpKotlinLike()}
================= IR: ========================
${ir.dumpSimple()}
================= Output Type: ========================
${writeOutput(ir)}
""".trimIndent()
}


}
