package io.exoquery.plugin.trees

import io.decomat.Is
import io.decomat.case
import io.decomat.on
import io.exoquery.CapturedBlock
import io.exoquery.annotation.ExoInsert
import io.exoquery.annotation.ExoUpdate
import io.exoquery.parseError
import io.exoquery.plugin.loc
import io.exoquery.plugin.location
import io.exoquery.plugin.ownerHasAnnotation
import io.exoquery.plugin.symName
import io.exoquery.plugin.transform.CX
import io.exoquery.xr.XR
import org.jetbrains.kotlin.ir.backend.js.utils.typeArguments
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.types.IrType

object ParseAction {
  context(CX.Scope, CX.Parsing, CX.Symbology)
  fun parse(expr: IrExpression) =
    on(expr).match<XR.Action> (
      // the `insert` part of capture { insert<Person> { set ... } }
      case(Ir.Call.FunctionMem1[Ir.Expr.ClassOf<CapturedBlock>(), Is.Companion.of("insert", "update"), Is.Companion()]).thenIfThis { _, _ -> ownerHasAnnotation<ExoInsert>() || ownerHasAnnotation<ExoUpdate>() }.thenThis { reciever, lambdaRaw ->
        val insertType = this.typeArguments.first() ?: parseError("Could not find the type argument of the insert/update call", expr)
        val compositeType = CompositeType.from(symName) ?: parseError("Unknown composite type: ${symName}", expr)

        on(lambdaRaw).match(
          case(Ir.FunctionExpression.withReturnOnlyBlock[Is.Companion()]).thenThis { blockBody ->
            val extensionParam = this.function.symbol.owner.extensionReceiverParameter
            val actionAlias = extensionParam?.makeIdent() ?: parseError("Could not find the extension receiver parameter of the insert/update call", expr)
            parseActionComposite(blockBody, insertType, actionAlias, compositeType)
          }
        ) ?: parseError("The statement inside of a insert/update block must be a single `set` or `setParams` expression followed by excluded, returning/Keys, or onConflict", lambdaRaw)
      },
    ) ?: parseError("Could not parse the action", expr)

  // TODO when going back to the Expression parser the 'this' pointer needs to be on the list of local symbols
  context(CX.Scope, CX.Parsing, CX.Symbology)
  private fun parseActionComposite(expr: IrExpression, inputType: IrType, actionAlias: XR.Ident, compositeType: CompositeType): XR.Action =
    // the i.e. insert { set(...) } or update { set(...) }
    on(expr).match<XR.Action>(
      case(Ir.Call.FunctionMem1[Ir.Expr.ClassOf<CapturedBlock>(), Is.Companion("set"), Ir.Vararg[Is.Companion()]]).then { _, (assignments) ->
        val ent = ParseQuery.parseEntity(inputType, expr.location())
        val parsedAssignments = assignments.map { parseAssignment(it) }
        when (compositeType) {
          CompositeType.Insert -> XR.Insert(ent, actionAlias, parsedAssignments, listOf(), expr.loc)
          CompositeType.Update -> XR.Update(ent, actionAlias, parsedAssignments, expr.loc)
        }
      }
    ) ?: parseError("Could not parse the expression inside of the action", expr)

  context(CX.Scope, CX.Parsing, CX.Symbology)
  private fun parseAssignment(expr: IrExpression): XR.Assignment =
    on(expr).match<XR.Assignment>(
      case(ExtractorsDomain.Call.`x to y`[Is.Companion(), Is.Companion()]).thenThis { left, right ->
        val property = ParseExpression.parse(left).let { it as? XR.Property ?: parseError("Could not parse the left side of the assignment: ${it.showRaw()}", left) }
        XR.Assignment(property, ParseExpression.parse(right), expr.loc)
      }
    ) ?: parseError("Could not parse the assignment", expr)

  sealed interface CompositeType {
    object Insert: CompositeType; object Update: CompositeType

    companion object {
      fun from(str: String) =
        when (str) {
          "insert" -> Insert
          "update" -> Update
          else -> null
        }
    }
  }

}
