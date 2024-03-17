package io.exoquery.norm

import io.exoquery.xr.XR
import io.exoquery.xr.cs
import io.exoquery.xrError


/**
 * Find the innermost clause that contains an entity and attach some other kind
 * of clause into it, e.g. a Filter. For example if you have an AST that looks
 * like this:
 * {{{
 *   FlatMap(Map(Entity(A), a, B), b, C)
 * }}}
 * Then `AttachToEntity(Filter(_, _, Cond)` will result in this:
 * {{{
 *   FlatMap(Map(Filter(Entity(A), {tmp}, Cond), a, B), b, C)
 * }}}
 *
 * Note how the inner ident `{tmp}` needs to be unique and not conflict with any
 * ident higher in the AST that is used inside the Cond clause, otherwise, the
 * various kinds of ASTs will be irreversibly corrupted. Here is an example:
 *
 * Take:
 * {{{
 *   FlatMap(A, a, Entity(C))
 * }}}
 *
 * Attached to the clause:
 * {{{
 *   Filter(_, {dangerous_tmp}, If(a == x, foo, bar))
 * }}}
 *
 * Which results in:
 * {{{
 *   FlatMap(A, a, Filter(Entity(C), {dangerous_tmp}, If(a == x, foo, bar))
 * }}}
 *
 * If `{dangerous_tmp}` is the Ident 'a' then the following happens: (I have
 * added curly braces {} around this Ident just to distinguish it)
 * {{{
 *   FlatMap(A, a, Filter(Entity(C), {a}, If(b == x, foo, bar))
 * }}}
 * At that point the 'a' inside the attached Filter and the outside FlatMap are
 * indistinguishable and indeed, the next phase of `AvoidAliasConflict` will
 * likely turn this expression into the following:
 * {{{
 *   FlatMap(A, a, Filter(Entity(C), {a1}, If(a1 == x, foo, bar))
 * }}}
 * This is of course completely incorrect because the ident {a1} should actually
 * be {a} referring to the ident of the outer FlatMap.
 */
object AttachToEntity {
  fun XR.Query.isEntity() = this is XR.Entity || this is XR.Infix

  operator fun invoke(f: (XR.Query, XR.Ident) -> XR.Query, alias: XR.Ident? = null): (XR.Query) -> XR.Query = { q ->
    applyWithId(f, alias, 0)(q)
  }

// Scala:
// def apply(f: (Ast, Ident) => Query, alias: Option[Ident] = None)(q: Ast): Ast = applyWithId(f, alias, 0)(q)

  private fun applyWithId(f: (XR.Query, XR.Ident) -> XR.Query, alias: XR.Ident?, nextId: Long): (XR.Query) -> XR.Query = { q ->
    with(q) {
      when {
        this is XR.Map && head.isEntity() -> XR.Map.cs(f(head, id), id, body)
        this is XR.FlatMap && head.isEntity() -> XR.FlatMap.cs(f(head, id), id, body)
        this is XR.ConcatMap && head.isEntity() -> XR.ConcatMap.cs(f(head, id), id, body)
        this is XR.Filter && head.isEntity() -> XR.Filter.cs(f(head, id), id, body)
        this is XR.SortBy && head.isEntity() -> XR.SortBy.cs(f(head, id), id, criteria, ordering)
        this is XR.DistinctOn && head.isEntity() -> XR.DistinctOn.cs(f(head, id), id, by)

        this is XR.GroupByMap || this is XR.Union || this is XR.UnionAll || this is XR.FlatJoin ->
          f(this, alias ?: XR.Ident("x", type, loc))

        this is XR.Map -> XR.Map.cs(applyWithId(f, id, nextId + 1)(head), id, body)
        this is XR.FlatMap -> XR.FlatMap.cs(applyWithId(f, id, nextId + 1)(head), id, body)
        this is XR.ConcatMap -> XR.ConcatMap.cs(applyWithId(f, id, nextId + 1)(head), id, body)
        this is XR.Filter -> XR.Filter.cs(applyWithId(f, id, nextId + 1)(head), id, body)
        this is XR.SortBy -> XR.SortBy.cs(applyWithId(f, id, nextId + 1)(head), id, criteria, ordering)
        this is XR.Take -> XR.Take.cs(applyWithId(f, alias, nextId + 1)(head), num)
        this is XR.Drop -> XR.Drop.cs(applyWithId(f, alias, nextId + 1)(head), num)
        // Note that Aggregation is not here because in ExoQuery XR.Aggregation is not a type of XR.Query
        this is XR.Distinct -> XR.Distinct.cs(applyWithId(f, alias, nextId + 1)(head))
        this is XR.DistinctOn -> XR.DistinctOn.cs(applyWithId(f, id, nextId + 1)(head), id, by)

        this.isEntity() -> f(this, alias ?: XR.Ident("[tmp_attachtoentity${nextId}]", type, loc))
        else -> xrError("Can't find an 'Entity' in '$q'")
      }
    }
  }
}

/*
Scala:

object AttachToEntity {
  private object IsEntity { def unapply(q: Ast): Option[Ast] = q match { case q: Entity => Some(q); case q: Infix  => Some(q); case _         => None } }

  private def applyWithId(f: (Ast, Ident) => Query, alias: Option[Ident], nextId: Long)(q: Ast): Ast =
    q match {

      case Map(IsEntity(a), b, c)        => Map(f(a, b), b, c)
      case FlatMap(IsEntity(a), b, c)    => FlatMap(f(a, b), b, c)
      case ConcatMap(IsEntity(a), b, c)  => ConcatMap(f(a, b), b, c)
      case Filter(IsEntity(a), b, c)     => Filter(f(a, b), b, c)
      case SortBy(IsEntity(a), b, c, d)  => SortBy(f(a, b), b, c, d)
      case DistinctOn(IsEntity(a), b, c) => DistinctOn(f(a, b), b, c)

      case _: GroupByMap | _: Union | _: UnionAll | _: FlatJoin =>
        f(q, alias.getOrElse(Ident("x", q.quat)))

      case Map(a: Query, b, c)        => Map(applyWithId(f, Some(b), nextId + 1)(a), b, c)
      case FlatMap(a: Query, b, c)    => FlatMap(applyWithId(f, Some(b), nextId + 1)(a), b, c)
      case ConcatMap(a: Query, b, c)  => ConcatMap(applyWithId(f, Some(b), nextId + 1)(a), b, c)
      case Filter(a: Query, b, c)     => Filter(applyWithId(f, Some(b), nextId + 1)(a), b, c)
      case SortBy(a: Query, b, c, d)  => SortBy(applyWithId(f, Some(b), nextId + 1)(a), b, c, d)
      case Take(a: Query, b)          => Take(applyWithId(f, alias, nextId + 1)(a), b)
      case Drop(a: Query, b)          => Drop(applyWithId(f, alias, nextId + 1)(a), b)
      case Distinct(a: Query)         => Distinct(applyWithId(f, alias, nextId + 1)(a))
      case DistinctOn(a: Query, b, c) => DistinctOn(applyWithId(f, Some(b), nextId + 1)(a), b, c)

      case IsEntity(q) => f(q, alias.getOrElse(Ident(s"[tmp_attachtoentity${nextId}]", q.quat)))
      case other       => fail(s"Can't find an 'Entity' in '$q'")
    }
}

 */