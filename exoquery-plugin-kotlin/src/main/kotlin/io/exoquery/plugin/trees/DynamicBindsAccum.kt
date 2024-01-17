package io.exoquery.plugin.trees

import io.exoquery.BID
import io.exoquery.DynamicBinds
import io.exoquery.plugin.transform.BuilderContext
import io.exoquery.pprint
import org.jetbrains.kotlin.ir.expressions.IrExpression

class DynamicBindsAccum {
  private val binds = mutableListOf<Pair<BID, RuntimeBindValueExpr>>()

  fun add(bindId: BID, bind: RuntimeBindValueExpr) {
    binds.add(bindId to bind)
  }

  fun show() = pprint(binds)

  fun getBinds() = binds

  companion object {
    fun empty() = DynamicBindsAccum()
  }
}

// create an IrExpression DynamicBind(listOf(Pair(BID, RuntimeBindValue), etc...))
context(BuilderContext) fun DynamicBindsAccum.makeDynamicBindsIr(): IrExpression {
  return with (makeLifter()) {
    val bindsList = getBinds().map { pair ->
      pair.lift(
        {bid -> bid.lift()},
        { it.makeDynamicBindsIr() })
    }
    make<DynamicBinds>(bindsList.liftExpr<Pair<BID, IrExpression>>())
  }
}
