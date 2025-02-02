package io.exoquery.xr

import io.exoquery.pprint.PPrinterConfig
import io.exoquery.pprint.Tree
import io.exoquery.printing.PrintSkipLoc
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

// The structure should be:
// val from: SX.From, val joins: List<SX.JoinClause>, val where: SX.Where?, val groupBy: SX.GroupBy?, val sortBy: SX.SortBy?
@Serializable
data class SelectClause(
  val from: List<SX.From>,
  val joins: List<SX.Join>,
  val where: SX.Where?,
  val groupBy: SX.GroupBy?,
  val sortBy: SX.SortBy?,
  val select: XR.Expression,
  override val type: XRType,
  override val loc: XR.Location = XR.Location.Synth
): XR.CustomQuery.Convertable {

  override fun toQueryXR(): XR.Query = SelectClauseToXR(this)
  fun allComponents(): List<SX> = from + joins + listOfNotNull(where, groupBy, sortBy)

  // Do nothing for now, in the cuture recurse in queries and expressions inside the SX clauses
  override fun handleStatelessTransform(transformer: StatelessTransformer): XR.CustomQuery = this

  // Do nothing for now, in the cuture recurse in queries and expressions inside the SX clauses
  override fun <S> handleStatefulTransformer(transformer: StatefulTransformer<S>): Pair<XR.CustomQuery, StatefulTransformer<S>> = this to transformer
  override fun showTree(config: PPrinterConfig): Tree = PrintSkipLoc<SelectClause>(serializer(), config).treeify(this, null, false, false)

  companion object {
    fun justSelect(select: XR.Expression, loc: XR.Location): SelectClause = SelectClause(emptyList(), emptyList(), null, null, null, select, select.type, loc)

    // A friendlier constructor for tests
    fun of (
      from: List<SX.From>,
      joins: List<SX.Join> = listOf(),
      where: SX.Where? = null,
      groupBy: SX.GroupBy? = null,
      sortBy: SX.SortBy? = null,
      select: XR.Expression,
      type: XRType,
      loc: XR.Location = XR.Location.Synth
    ): SelectClause = SelectClause(from, joins, where, groupBy, sortBy, select, type, loc)
  }

  fun toXrTransform(): XR.Query = SelectClauseToXR(this)
  fun toXrRef(): XR.CustomQueryRef = XR.CustomQueryRef(this)


  data class Id(val from: List<SX.From>, val joins: List<SX.Join>, val where: SX.Where?, val groupBy: SX.GroupBy?, val sortBy: SX.SortBy?, val select: XR.Expression)
  @Transient
  val id = Id(from, joins, where, groupBy, sortBy, select)
  override fun equals(other: Any?): Boolean = (this === other) || (other is SelectClause && id == other.id)
  override fun hashCode(): Int = id.hashCode()
}
