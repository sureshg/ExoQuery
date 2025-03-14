package io.exoquery.plugin.logging

import io.exoquery.annotation.CapturedFunction
import io.exoquery.plugin.dataClassProperties
import io.exoquery.plugin.printing.dumpSimple
import io.exoquery.plugin.safeName
import io.exoquery.plugin.source
import io.exoquery.plugin.transform.CX
import io.exoquery.plugin.transform.dumpKotlinLikePretty
import io.exoquery.plugin.trees.showLineage
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.*

object Messages {

val InvalidColumnExclusions =
"""
Invalid columns were used in the `excluding` function.
The `excluding` function is called from an `insert` or `update` action when a setParams function
is used, in order to exlcude generated-columns from the insert or update query. For example:

data class Person(val id: Int, val name: String, val age: Int)
val joe = Person(1, "Joe", 123)

val insertPerson = capture {
  insert<Person> {
    setParams(joe).excluding(id)
  }
}

This will generate a insert query that skips the `id` column.
INSERT INTO Person (name, age) VALUES (?, ?)
""".trimIndent()

fun InvalidSqlActionFunctionBody() =
"""
The SqlAction expression has an invalid structure. An SqlAction expression should be a lambda with a single expression. For example:

val insertPerson = capture {
  insert<Person> { set(name to "Joe", age to 123) }
}
""".trimIndent()

fun CannotCallUseOnAnArbitraryDynamic() =
"""
Could not understand the SqlExpression (from the scaffold-call) that you are attempting to call `.use` on. You can only call `.use` on a variable type as SqlExpression.
If you are attempting to use an expression here, it is best practice to write it into a variable outside the capture-block and then call `.use` on that variable. If
this is a function that you are sure can be safely spliced (e.g. it is a pure-function that does not have side-effects) then you can use the @CapturedDynamic annotation
on the function to allow it to be used in this context.
""".trimIndent()

context(CX.Scope)
fun ValueLookupComingFromExternalInExpression(variable: IrGetValue, captureTypeName: String = "select") =
"""
It looks like the variable `${variable.symbol.safeName}` is coming from outside the capture/${captureTypeName} block. Typically
this is a runtime-value that you need to bring into the query as a parameter like this: `param(${variable.symbol.safeName})`.
For example:

val nameVariable = "Joe"
val query = select { Table<Person>().filter { p -> p.name == param(nameVariable) } }
> This will create the query:
> SELECT p.id, p.name, p.age FROM Person p WHERE p.name = ?

(Lineage: ${variable.showLineage()})
""".trimIndent()

context(CX.Scope)
fun VariableComingFromNonCapturedFunction(expr: IrExpression, funName: String) =
"""
It appears that the expression `${expr.source()}` is an argument coming from a function call which will force
the whole surrounding query to become dynamic. If the whole function `${funName}` just returns a SqlQuery and does nothing
else, annotate it as @CapturedFunction and you can then use it to build compile-time functions.
================= For example: =================

fun joes(people: SqlQuery<Person>) = capture { people.filter { p -> p.name == "Joe" } }
val myJoes = joes(Table<Person>()) // This will be dynamic

@CapturedFunction
fun joes(people: SqlQuery<Person>) = capture { people.filter { p -> p.name == "Joe" } }
val myJoes = joes(Table<Person>()) // Now it will be static
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

context(CX.Scope)
fun CapturedFunctionFormWrong(msg: String) =
"""
$msg

The form of the function annotated with @CapturedFunction is incorrect. It must be a function with a 
single output expression that returns a SqlQuery<T> instance.
================= For example: =================

@CapturedFunction
fun myFunction(): SqlQuery<Int> = capture { Table<Person>().map { it.age } }

@CapturedFunction
fun myFunction(): SqlQuery<Int> = select { 
  val p = from(Table<Person>())
  p.age
}
""".trimIndent()


context(CX.Scope)
fun ParserMessage(ir: IrExpression?, parsedCode: String?) =
"""
================ Parsed As: ================
$parsedCode
================ Interpreted IR: ================
${ir?.dumpKotlinLikePretty()}
================= Raw IR: ========================
${ir?.dumpSimple()}
""".trimIndent()

context(CX.Scope)
fun PrintingMessage(ir: IrExpression?) =
"""
================ Interpreated IR: ================
${ir?.dumpKotlinLikePretty()}
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
