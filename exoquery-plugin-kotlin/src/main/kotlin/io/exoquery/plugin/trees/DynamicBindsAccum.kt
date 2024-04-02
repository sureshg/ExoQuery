package io.exoquery.plugin.trees

import io.exoquery.BID
import io.exoquery.DynamicBinds
import io.exoquery.plugin.printing.PrintCompiletimes
import io.exoquery.plugin.transform.BuilderContext
import org.jetbrains.kotlin.ir.expressions.IrExpression

class DynamicBindsAccum {
  private val binds = mutableListOf<Pair<BID, RuntimeBindValueExpr>>()

  fun add(bindId: BID, bind: RuntimeBindValueExpr) {
    binds.add(bindId to bind)
  }

  operator fun plus(other: DynamicBindsAccum): DynamicBindsAccum {
    val newBinds = DynamicBindsAccum()
    newBinds.binds.addAll(this.binds)
    newBinds.binds.addAll(other.binds)
    return newBinds
  }


  fun show() = PrintCompiletimes()(binds)

  fun getBoth(): List<Pair<BID, RuntimeBindValueExpr>> = binds.toList()

  companion object {
    fun empty() = DynamicBindsAccum()
  }
}

// create an IrExpression DynamicBind(listOf(Pair(BID, RuntimeBindValue), etc...))
context(BuilderContext) fun DynamicBindsAccum.makeDynamicBindsIr(): IrExpression {
  return with (makeLifter()) {
    val bindsList = getBoth().map { pair ->
      pair.lift(
        {bid -> bid.lift()},
        { it.makeDynamicBindsIr() })
    }
    make<DynamicBinds>(bindsList.liftExpr<Pair<BID, IrExpression>>())
  }
}
