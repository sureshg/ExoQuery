package io.exoquery.plugin.trees

import io.exoquery.SX
import io.exoquery.plugin.logging.CompileLogger
import io.exoquery.xr.XR
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.expressions.IrExpression

object ValidateAndOrganize {
  private interface Phase {
    data object FROM: Phase
    data object JOIN: Phase
    data object MODIFIER: Phase // I.e. for group/sort/take/drop
  }

  // The "pure functiona' way to do this would be to have a State object that is copied with new values but since
  // everything here is completely private and the only way to interact with it is through the invoke function, we it should be fine for now.
  private class State(var phase: Phase = Phase.FROM, val froms: MutableList<SX.From> = mutableListOf(), val joins: MutableList<SX.Join> = mutableListOf(), var where: SX.Where? = null, var groupBy: SX.GroupBy? = null, var sortBy: SX.SortBy? = null) {
    fun validPhases(vararg phases: Phase) = { errorMsg: String ->
      if (phases.contains(phase)) error(errorMsg)
    }
    fun setPhase(phase: Phase) {
      this.phase = phase
    }

    context(ParserContext, CompileLogger)
    fun addFrom(from: SX.From, expr: IrElement) {
      validPhases(Phase.FROM)("Cannot add a Fom clause after a Join clause or any other clause")
      setPhase(Phase.FROM)
      froms += from
    }
    context(ParserContext, CompileLogger)
    fun addJoin(join: SX.Join, expr: IrElement) {
      validPhases(Phase.JOIN, Phase.FROM)("Cannot only add JOIN clauses after From and before any Where/Group/Sort clauses. ")
      setPhase(Phase.JOIN)
      joins += join
    }
    context(ParserContext, CompileLogger)
    fun addWhere(where: SX.Where, expr: IrElement) {
      //if (phase != Phase.JOIN && phase != Phase.FROM) error("", expr)
      //phase = Phase.MODIFIER
      validPhases(Phase.JOIN, Phase.FROM, Phase.MODIFIER)("Only one `WHERE` clause is allowed an it must be after from/join calls and before any group/sort clauses")
      setPhase(Phase.MODIFIER) // Basically `WHERE` is like it's on phase but we don't need to create one since it can only occur once
      this.where = where
    }
    context(ParserContext, CompileLogger)
    fun addGroupBy(groupBy: SX.GroupBy, expr: IrElement) {
      validPhases(Phase.JOIN, Phase.FROM, Phase.MODIFIER)("Only one `GROUP BY` clause is allowed an it must be after from/join calls and before any sort clauses")
      setPhase(Phase.MODIFIER)
      this.groupBy = groupBy
    }
    context(ParserContext, CompileLogger)
    fun addSortBy(sortBy: SX.SortBy, expr: IrElement) {
      validPhases(Phase.JOIN, Phase.FROM, Phase.MODIFIER)("Only one `SORT BY` clause is allowed an it must be after from/join calls and before any group clauses")
      setPhase(Phase.MODIFIER)
      this.sortBy = sortBy
    }
  }

  context(ParserContext, CompileLogger)
  operator fun invoke(statements: List<Pair<SX, IrStatement>>, ret: XR.Expression): SX.Select {
    val state = State()
    statements.forEach { (sx, stmt) ->
      when (sx) {
        is SX.From -> state.addFrom(sx, stmt)
        is SX.Join -> state.addJoin(sx, stmt)
        is SX.Where -> state.addWhere(sx, stmt)
        is SX.GroupBy -> state.addGroupBy(sx, stmt)
        is SX.SortBy -> state.addSortBy(sx, stmt)
        is SX.Select -> error("Cannot have a SELECT clause in this context", stmt)
      }
    }
    return SX.Select(state.froms, state.joins, state.where, state.groupBy, state.sortBy, ret)
  }

}