package io.exoquery.plugin.printing

import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.util.dumpKotlinLike

object Errors {
  fun NoDispatchRecieverFoundForSqlVarCall(sqlVar: IrExpression): Nothing =
    throw IllegalStateException(Messages.NoDispatchRecieverFoundForSqlVarCall(sqlVar))
}

object Messages {

fun NoDispatchRecieverFoundForSqlVarCall(sqlVar: IrExpression) =
"""
No dispatch-reciver was found for the SqlVariable:
${sqlVar.dumpKotlinLike()}

This should be impossible.
""".trimIndent()

}
