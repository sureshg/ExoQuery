package io.exoquery.sql

import io.exoquery.xr.`+==+`
import io.exoquery.xr.`+and+`
import io.exoquery.xr.`+or+`
import io.exoquery.xr.BinaryOperator
import io.exoquery.xr.OP
import io.exoquery.xr.StatelessTransformer
import io.exoquery.xr.XR
import io.exoquery.xr.XRType
import io.exoquery.xr.copy.BinaryOp
import io.exoquery.xr.copy.Filter
import io.exoquery.xr.copy.FlatJoin
import io.exoquery.xr.copy.When
import io.exoquery.xr.copy.Map
import io.exoquery.xr.copy.Product
import io.exoquery.xr.copy.UnaryOp
import io.exoquery.xr.copy.cs
import io.exoquery.xr.csf

object VendorizeBooleans : StatelessTransformer {
  //  override def apply(ast: Ast): Ast =
  //    ast match {
  //      // Map clauses need values e.g. map(n=>n.status==true) => map(n=>if(n.status==true) 1 else 0)
  //      case Map(q, alias, body) =>
  //        Map(apply(q), alias, valuefyExpression(apply(body)))
  //      case Filter(q, alias, body) =>
  //        Filter(apply(q), alias, expressifyValue(apply(body)))
  //      case FlatJoin(typ, a, aliasA, on) =>
  //        FlatJoin(typ, a, aliasA, expressifyValue(apply(on)))
  override fun invoke(xr: XR.Query): XR.Query =
    with(xr) {
      when (this) {
        // Map clauses need values e.g. map(n=>n.status==true) => map(n=>if(n.status==true) 1 else 0)
        is XR.Map -> Map.cs(invoke(head), id, valuefyExpression(invoke(body)))
        // Filter clauses need expressions e.g. filter(n=>n.isTrue) becomes filter(n=>n.isTrue==1)
        is XR.Filter -> Filter.cs(invoke(head), id, expressifyValue(body))
        // FlatJoin clauses need expressions e.g. flatJoin(n=>n.isTrue) becomes flatJoin(n=>n.isTrue==1)
        is XR.FlatJoin -> FlatJoin.cs(invoke(head), id, expressifyValue(on))
        is XR.FlatFilter -> XR.FlatFilter.csf(expressifyValue(by))(this)
        else -> super.invoke(xr)
      }
    }


  override fun invoke(xr: XR.Expression): XR.Expression =
    with(xr) {
      when {
        // Things that have ||, && between them are typically expressions, things like "true || e.isSomething"
        // need to be converted to "true == true || e.isSomething == true" so they are
        // tokenized as "1 == 1 || e.isSomething == 1"
        this is XR.BinaryOp && isOperatorOnExpressions(op) ->
          BinaryOp.cs(expressifyValue(invoke(a)), op, expressifyValue(invoke(b)))
        this is XR.BinaryOp && isOperatorOnValues(op) ->
          BinaryOp.cs(valuefyExpression(invoke(a)), op, valuefyExpression(invoke(b)))
        // Example: "q.filter(e => !e.isSomething)" which needs to be converted to
        // "q.filter(e => !(e.isSomething == 1))" so it can be tokenized to "... WHERE e.isSomething = 1
        this is XR.UnaryOp && op == OP.not ->
          UnaryOp.cs(OP.not, expressifyValue(invoke(expr)))

        this is XR.Product ->
          Product.cs(fields.map { (name, value) -> name to valuefyExpression(invoke(value)) })

        this is XR.When -> {
          When.cs(
            branches.map {
              XR.Branch.csf(expressifyValue(invoke(it.cond)), valuefyExpression(invoke(it.then)))(it)
            }, valuefyExpression(invoke(orElse))
          )
        }

        else -> super.invoke(xr)
      }
    }

//  override def apply(operation: Operation): Operation = {
//    import BooleanOperator._
//
//    operation match {
//      // Things that have ||, && between them are typically expressions, things like "true || e.isSomething"
//      // need to be converted to "true == true || e.isSomething == true" so they are
//      // tokenized as "1 == 1 || e.isSomething == 1"
//      case BinaryOperation(a, OperatorOnExpressions(op), b) =>
//        BinaryOperation(expressifyValue(apply(a)), op, expressifyValue(apply(b)))
//      case BinaryOperation(a, OperatorOnValues(op), b) => {
//        BinaryOperation(valuefyExpression(apply(a)), op, valuefyExpression(apply(b)))
//      }
//      // Example: "q.filter(e => !e.isSomething)" which needs to be converted to
//      // "q.filter(e => !(e.isSomething == 1))" so it can be tokenized to "... WHERE e.isSomething = 1
//      case UnaryOperation(`!`, ast) =>
//        UnaryOperation(BooleanOperator.`!`, expressifyValue(apply(ast)))
//      case CaseClass(n, values) =>
//        CaseClass(n, values.map { case (name, value) => (name, valuefyExpression(apply(value))) })
//      case If(cond, t, e) =>
//        If(expressifyValue(apply(cond)), valuefyExpression(apply(t)), valuefyExpression(apply(e)))
//      case _ =>
//        super.apply(ast)
//    }
//  }


  fun isOperatorOnExpressions(op: BinaryOperator) =
    when (op) {
      OP.or, OP.and -> true
      else -> false
    }

//    def isOperatorOnExpressions(op: BinaryOperator) =
//      op match {
//        case `||` | `&&` => Some(op)
//        case _           => None
//      }
//  }
//


  fun isOperatorOnValues(op: BinaryOperator) =
    when (op) {
      OP.`==`, OP.`!=`, OP.gt, OP.gte, OP.lt, OP.lte -> true
      else -> false
    }

//    def isOperatorOnValues(op: BinaryOperator) =
//      op match {
//        case `<` | `>` | `<=` | `>=` | EqualityOperator.`_==` | EqualityOperator.`_!=` => Some(op)
//        case _                                                                         => None
//      }
//  }

  fun valuefyExpression(xr: XR.Expression): XR.Expression =
    with(xr) {
      when {
        type is XRType.BooleanExpression ->
          XR.When.makeIf(this, XR.Const.Boolean(true), XR.Const.Boolean(false), loc)
        else ->
          xr
      }
    }


//  def valuefyExpression(ast: Ast): Ast = ast.quat match {
//    case BooleanExpression => If(ast, Constant(true, BooleanValue), Constant(false, BooleanValue))
//    case _                 => ast
//  }


  fun XR.When.allPartsBooleanValue(): Boolean =
    branches.all { it.then.isBooleanValue() } && orElse.isBooleanValue()

  fun XR.When.reduceToExpression(): XR.Expression = run {
    val conds = branches.map { expressifyValue(it.cond) }.reduce { a, b -> a `+and+` b }
    val condThens = branches.map { expressifyValue(it.cond) `+and+` expressifyValue(it.then) }
    val elseExpr = expressifyValue(orElse)
    condThens.reduce { a, b -> a `+and+` b } `+or+` (XR.UnaryOp(OP.not, conds) `+and+` elseExpr)
  }


  /*
   * Generally speaking you need to add true==X to some X which is a boolean-value to make it a boolean-expression
   * For example:
   * SELECT ... WHERE person.isSober
   *   => SELECT ... WHERE true==person.isSober
   * However if you know the expression is conditional you can do better:
   * SELECT ... WHERE CASE WHEN person.isSober THEN canDrinkMoreHere ELSE canGoToAnotherClub
   *   => SELECT ... WHERE (person.isSober && canDrinkMoreHere) || (!person.isSober && canGoToAnotherClub)
   */
  fun expressifyValue(xr: XR.Expression): XR.Expression =
    with(xr) {
      when {
        this is XR.When && this.allPartsBooleanValue() ->
          this.reduceToExpression()
        this.isBooleanValue() ->
          XR.Const.Boolean(true) `+==+` xr
        else ->
          xr
      }
    }


//  def expressifyValue(ast: Ast): Ast = ast match {
//    case If(condition, HasBooleanQuat(thenClause), HasBooleanQuat(elseClause)) =>
//      val condExpr = expressifyValue(condition)
//      val thenExpr = expressifyValue(thenClause)
//      val elseExpr = expressifyValue(elseClause)
//      (condExpr +&&+ thenExpr) +||+ (UnaryOperation(BooleanOperator.`!`, condExpr) +&&+ elseExpr)
//    case HasBooleanValueQuat(ast) =>
//      Constant(true, BooleanValue) +==+ ast
//    case _ =>
//      ast
//  }
}
