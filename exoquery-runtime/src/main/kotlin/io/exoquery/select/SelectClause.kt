package io.exoquery.select

import io.exoquery.Lambda1Expression
import io.exoquery.Query
import io.exoquery.QueryContainer
import io.exoquery.SqlVariable
import io.exoquery.annotation.ExoInternal
import io.exoquery.xr.XR
import io.exoquery.xr.XRType

@OptIn(ExoInternal::class) // TODO Not sure if the output here QueryContainer(Ident(SqlVariable)) is right need to look into the shape
class SelectClause<A> : ProgramBuilder<Query<A>, SqlVariable<A>>({ result -> QueryContainer<A>(XR.Ident(result.getVariableName(), XRType.Generic))  }) {

  public suspend fun <R> from(query: Query<R>): SqlVariable<R> =
    fromAliased(query, "x")

  public suspend fun <R> fromAliased(query: Query<R>, alias: String): SqlVariable<R> =
    perform { mapping ->
      val sqlVar = SqlVariable<R>(alias)
      val resultQuery = mapping(sqlVar)
      val ident = XR.Ident(sqlVar.getVariableName(), resultQuery.xr.type)
      QueryContainer<R>(XR.FlatMap(query.xr, ident, resultQuery.xr)) as Query<A> // TODO Unsafe cast, will this work?
    }

  public suspend fun <Q: Query<R>, R> join(query: Q) = JoinOn<Q, R, A>(query, XR.JoinType.Inner, this, null)

  // TODO public suspend fun <Q: Query<R>, R, A> joinAliased(query: Q, alias: String) = JoinOn(query, XR.JoinType.Inner, this, alias)
}

class JoinOn<Q: Query<R>, R, A>(private val query: Q, private val joinType: XR.JoinType, private val selectClause: SelectClause<A>, private val alias: String?) {
  suspend fun on(cond: (R).() -> Boolean): SqlVariable<R> =
    error("The join.on(...) expression of the Query was not inlined")

  // TODO some internal annotation?
  @OptIn(ExoInternal::class)
  suspend fun onExpr(cond: Lambda1Expression): SqlVariable<R> =
    with (selectClause) {
      // TODO if (alias != null) beta-reduce 'this' to the alias
      perform { mapping ->
        val sqlVariable = SqlVariable<R>(cond.ident.name)
        val outputQuery = mapping(sqlVariable)
        val ident = XR.Ident(sqlVariable.getVariableName(), outputQuery.xr.type)
        QueryContainer<R>(XR.FlatMap(
          XR.FlatJoin(joinType, query.xr, cond.ident, cond.xr.body), ident, outputQuery.xr)
        ) as Query<A>
      }
    }
}

/**
 * Query<Person> -> Query<Tuple>
 * person.map(p => tupleOf(p.name))
 */


//fun <T: Element, R: Element> Query<T>.flatMap(f: (T) -> Query<R>) = FlatMap(this, f(this.ent.onBind()))
//fun <T: Element, R: Element> Query<T>.map(f: (T) -> R) = Map(this, f(this.ent.onBind()))
////fun <T: Entity, E> Query<T>.map(f: (T) -> Expression<E>) = Map(this, Entity.Single(f(this.ent.onBind())))
//
//fun <A: Element> Query<A>.nested(): Query<A> = Nested(this)
//
////
//

//
//class IdGrantor()

