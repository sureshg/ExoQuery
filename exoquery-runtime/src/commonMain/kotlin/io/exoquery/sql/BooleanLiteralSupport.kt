package io.exoquery.sql

import io.exoquery.xr.XR
import io.exoquery.xr.XRType

interface BooleanLiteralSupport: SqlIdiom {
  override fun normalizeQuery(xr: XR.Query): XR.Query = run {
    val norm = super.normalizeQuery(xr)
    val vendorized = VendorizeBooleans(norm)
    vendorized
  }

  override val XR.Expression.token get(): Token =
    when {
      this is XR.Const.Boolean && type is XRType.BooleanValue ->
        StringToken(if (value) "1" else "0")
      this is XR.Const.Boolean && type is XRType.BooleanExpression ->
        StringToken(if (value) "1 = 1" else "1 = 0")
      else ->
        super.xrExpressionTokenImpl(this)
    }
}

//trait BooleanLiteralSupport extends SqlIdiom {
//
//  override def normalizeAst(
//    ast: Ast,
//    concatBehavior: ConcatBehavior,
//    equalityBehavior: EqualityBehavior,
//    idiomContext: IdiomContext
//  ) = {
//    val norm = SqlNormalize(ast, idiomContext.config, makeCache(), concatBehavior, equalityBehavior)
//    if (Messages.smartBooleans)
//      VendorizeBooleans(norm)
//    else
//      norm
//  }
//
//  override implicit def valueTokenizer(implicit
//    astTokenizer: Tokenizer[Ast],
//    strategy: NamingStrategy
//  ): Tokenizer[Value] =
//    Tokenizer[Value] {
//      case Constant(b: Boolean, Quat.BooleanValue) =>
//        StringToken(if (b) "1" else "0")
//      case Constant(b: Boolean, Quat.BooleanExpression) =>
//        StringToken(if (b) "1 = 1" else "1 = 0")
//      case other =>
//        super.valueTokenizer.token(other)
//    }
//}
