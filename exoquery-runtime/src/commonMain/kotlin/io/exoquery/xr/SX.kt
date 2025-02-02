package io.exoquery.xr

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

// Unline XR, SX is not a recursive AST, is merely a prefix AST that has a common-base class
@Serializable
sealed interface SX {
  @Serializable
  data class From(val variable: XR.Ident, val xr: XR.Query, val loc: XR.Location = XR.Location.Synth): SX {
    data class Id(val variable: XR.Ident, val xr: XR.Query)
    @Transient
    val id = Id(variable, xr)
    override fun equals(other: Any?): Boolean = if (this === other) true else other is From && id == other.id
    override fun hashCode(): Int = id.hashCode()
  }
  @Serializable
  data class Join(val joinType: XR.JoinType, val variable: XR.Ident, val onQuery: XR.Query, val conditionVariable: XR.Ident, val condition: XR.Expression, val loc: XR.Location = XR.Location.Synth): SX {
    data class Id(val joinType: XR.JoinType, val variable: XR.Ident, val onQuery: XR.Query, val conditionVariable: XR.Ident, val condition: XR.Expression)
    @Transient
    val id = Id(joinType, variable, onQuery, conditionVariable, condition)
    override fun equals(other: Any?): Boolean = if (this === other) true else other is Join && id == other.id
    override fun hashCode(): Int = id.hashCode()
  }
  @Serializable
  data class Where(val condition: XR.Expression, val loc: XR.Location = XR.Location.Synth): SX {
    data class Id(val condition: XR.Expression)
    @Transient
    val id = Id(condition)
    override fun equals(other: Any?): Boolean = if (this === other) true else other is Where && id == other.id
    override fun hashCode(): Int = id.hashCode()
  }
  @Serializable
  data class GroupBy(val grouping: XR.Expression, val loc: XR.Location = XR.Location.Synth): SX {
    data class Id(val grouping: XR.Expression)
    @Transient
    val id = Id(grouping)
    override fun equals(other: Any?): Boolean = if (this === other) true else other is GroupBy && id == other.id
    override fun hashCode(): Int = id.hashCode()
  }
  @Serializable
  data class SortBy(val sorting: XR.Expression, val ordering: XR.Ordering, val loc: XR.Location = XR.Location.Synth): SX {
    data class Id(val sorting: XR.Expression, val ordering: XR.Ordering)
    @Transient
    val id = Id(sorting, ordering)
    override fun equals(other: Any?): Boolean = if (this === other) true else other is SortBy && id == other.id
    override fun hashCode(): Int = id.hashCode()
  }
}
