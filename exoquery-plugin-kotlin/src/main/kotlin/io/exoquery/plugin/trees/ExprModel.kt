package io.exoquery.plugin.trees

import io.exoquery.BID
import io.exoquery.Runtimes
import io.exoquery.plugin.transform.BuilderContext
import org.jetbrains.kotlin.ir.expressions.IrExpression

class RuntimesExpr(val runtimes: List<Pair<BID, IrExpression>>) {
  context(BuilderContext) fun lift(): IrExpression {
    return with (makeLifter()) {
      val bindsList = runtimes.map { pair ->
        pair.lift(
          {bid -> bid.lift()},
          { it })
      }
      make<Runtimes>(bindsList.liftExpr<Pair<BID, IrExpression>>())
    }
  }
}

// create an IrExpression DynamicBind(listOf(Pair(BID, RuntimeBindValue), etc...))
