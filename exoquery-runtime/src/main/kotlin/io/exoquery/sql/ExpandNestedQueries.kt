package io.exoquery.sql

import io.decomat.*
import io.exoquery.xr.Is
import io.exoquery.xr.XR
import io.exoquery.xr.XRType
import io.exoquery.xr.get


class ExpandSelection(val from: List<FromContext>) {

  /** For external things to potentially use ExpandSelection */
  fun ofTop(values: List<SelectValue>, topLevelQuat: XRType): List<SelectValue> =
    invoke(values, QueryLevel.Top(topLevelQuat))

  fun ofSubselect(values: List<SelectValue>): List<SelectValue> =
    invoke(values, QueryLevel.Inner)

  internal fun invoke(values: List<SelectValue>, level: QueryLevel): List<SelectValue> =
    values.flatMap { invoke(it, level) }

  fun String?.concatWith(str: String): String = "${this ?: ""}${str}"

  internal fun invoke(value: SelectValue, level: QueryLevel): List<SelectValue> {
    fun concatOr(concatA: String?, concatB: String, or: String?) =
      if (!level.isTop)
        concatA.concatWith(concatB)
      else
        or

    // Assuming there's no case class or tuple buried inside or a property i.e. if there were,
    // the beta reduction would have unrolled them already
    return on(value).match(
      case(SelectValue[PropertyOrCore(), Is()]).thenThis { ast, alias ->
        // Whether or not we are in a concatMap-type of selection
        val concat = this.concat
        val alternateQuat =
          when(level) {
            is QueryLevel.Top -> level.topLevelQuat
            else              -> null
          }
        val exp = SelectPropertyProtractor(from)(ast, alternateQuat)
        exp.map { (p, path) ->
          when {
            // If the quat-path is nothing and there is some preexisting alias (e.g. if we came from a case-class or quat)
            // the use that. Otherwise the selection is of an individual element so use the element name (before the rename)
            // as the alias.
            p is XR.Property && path.isEmpty() ->
              SelectValue(p, alias ?: p.name)
            // Append alias headers (i.e. _1,_2 from tuples and field names foo,bar from case classes) to the
            // value of the XRType path
            p is XR.Property && path.isNotEmpty() ->
              SelectValue(p, concatOr(alias, path.joinToString(""), path.lastOrNull()), concat)
            else ->
              SelectValue(p, alias, concat)
          }
        }
      },
      case(SelectValue[XR.Product.Is, Is()]).thenThis { product, alias ->
        val concat = this.concat
        val fields = product.fields
        fields.flatMap { (name, ast) ->
          // Go into the select values, if the level is Top we need to go TopUnwrapped since the top-level
          // XRType doesn't count anymore. If level=Inner then it's the same.
          invoke(SelectValue(ast, concatOr(alias, name, name), concat), level.withoutTopQuat())
        }
      }


    ) // SelectValue(Direct) SelectValue(infix), etc...
    ?: listOf(value)
  }



///*
// * Much of what this does is documented in PRs here:
// * https://github.com/zio/zio-quill/pull/1920 and here:
// * https://github.com/zio/zio-quill/pull/2381 and here:
// * https://github.com/zio/zio-quill/pull/2420
// */
//object ExpandNestedQueries extends StatelessQueryTransformer {
//
//  protected override fun invoke(q: SqlQuery, level: QueryLevel): SqlQuery =
//    q match {
//      case q: FlattenSqlQuery =>
//        val selection = new ExpandSelection(q.from)(q.select, level)
//        val out       = expandNested(q.copy(select = selection)(q.quat), level)
//        out
//      case other =>
//        super.invoke(q, level)
//    }
//
//  case class FlattenNestedProperty(from: List<FromContext>) {
//    val inContext = InContext(from)
//
//    fun invoke(p: Ast): Ast =
//      p match {
//        case p @ PropertyMatryoshka(inner, path, renameables) =>
//          val isSubselect       = inContext.isSubselect(p)
//          val propsAlreadyFixed = renameables.forall(_ == Renameable.Fixed)
//          val isPropertyRenamed = p.prevName.isfunined
//          val renameable =
//            if (isPropertyRenamed || isSubselect || propsAlreadyFixed)
//              Renameable.Fixed
//            else
//              Renameable.ByStrategy
//
//          // If it is a sub-select or a renamed property, do not apply the strategy to the property
//          if (isSubselect)
//            Property.Opinionated(inner, path.mkString, renameable, Visibility.Visible)
//          else
//            Property.Opinionated(inner, path.last, renameable, Visibility.Visible)
//
//        case other => other
//      }
//
//    fun inside(ast: Ast) =
//      Transform(ast) { case p: Property =>
//        invoke(p)
//      }
//  }
//
//  protected override fun expandNested(q: FlattenSqlQuery, level: QueryLevel): FlattenSqlQuery =
//    q match {
//      case FlattenSqlQuery(from, where, groupBy, orderBy, limit, offset, select, distinct) =>
//        val flattenNestedProperty = FlattenNestedProperty(from)
//        val newFroms              = q.from.map(expandContextFlattenOns(_, flattenNestedProperty))
//
//        fun distinctIfNotTopLevel(values: List<SelectValue>) =
//          if (level.isTop)
//            values
//          else
//            values.distinct
//
//        /*
//         * In sub-queries, need to make sure that the same field/alias pair is not selected twice
//         * which is possible when aliases are used. For example, something like this:
//         *
//         * case class Emb(id: Int, name: String)
//         * case class Parent(id: Int, name: String, emb: Emb)
//         * case class GrandParent(id: Int, par: Parent)
//         * val q = quote { query<GrandParent>.map(g => g.par).distinct.map(p => (p.name, p.emb, p.id, p.emb.id)).distinct.map(tup => (tup._1, tup._2, tup._3, tup._4)).distinct }
//         * Will cause double-select inside the innermost subselect:
//         * SELECT DISTINCT theParentName AS theParentName, id AS embid, theName AS embtheName, id AS id, id AS embid FROM GrandParent g
//         * Note how embid occurs twice? That's because (p.emb.id, p.emb) are expanded into (p.emb.id, p.emb.id, e.emb.name).
//         *
//         * On the other hand if the query is top level we need to make sure not to do this deduping or else the encoders won't work since they rely on clause positions
//         * For example, something like this:
//         * val q = quote { query<GrandParent>.map(g => g.par).distinct.map(p => (p.name, p.emb, p.id, p.emb.id)) }
//         * Would normally expand to this:
//         * SELECT p.theParentName, p.embid, p.embtheName, p.id, p.embid FROM ...
//         * Note now embed occurs twice? We need to maintain this because the second element of the output tuple
//         * (p.name, p.emb, p.id, p.emb.id) needs the fields p.embid, p.embtheName in that precise order in the selection
//         * or they cannot be encoded.
//         */
//        val distinctSelects =
//          distinctIfNotTopLevel(select)
//
//        val distinctKind =
//          q.distinct match {
//            case DistinctKind.DistinctOn(props) =>
//              DistinctKind.DistinctOn(props.map(p => flattenNestedProperty.inside(p)))
//            case other => other
//          }
//
//        q.copy(
//          select = distinctSelects.map(sv => sv.copy(ast = flattenNestedProperty.inside(sv.ast))),
//          from = newFroms,
//          where = where.map(flattenNestedProperty.inside(_)),
//          groupBy = groupBy.map(flattenNestedProperty.inside(_)),
//          orderBy = orderBy.map(ob => ob.copy(ast = flattenNestedProperty.inside(ob.ast))),
//          limit = limit.map(flattenNestedProperty.inside(_)),
//          offset = offset.map(flattenNestedProperty.inside(_)),
//          distinct = distinctKind
//        )(q.quat)
//    }
//
//  fun expandContextFlattenOns(s: FromContext, flattenNested: FlattenNestedProperty): FromContext = {
//    fun expandContextRec(s: FromContext): FromContext =
//      s match {
//        case QueryContext(q, alias) =>
//          QueryContext(invoke(q, QueryLevel.Inner), alias)
//        case JoinContext(t, a, b, on) =>
//          JoinContext(t, expandContextRec(a), expandContextRec(b), flattenNested.inside(on))
//        case FlatJoinContext(t, a, on) =>
//          FlatJoinContext(t, expandContextRec(a), flattenNested.inside(on))
//        case _: TableContext | _: InfixContext => s
//      }
//
//    expandContextRec(s)
//  }
}
