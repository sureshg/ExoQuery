package io.exoquery.printing

// @formatter:off
object MessagesRuntime {

val ReturningExplanation =
"""
----------------------------- About Returning Clauses -----------------------------
The `returning` clause in ExoQuery will use a specific (dialect-dependent) construct in SQL to return
a set of columns from the inserted, updated, or deleted entity. This is not universally supported
across all databases and even the databases that support it do not have a mutual consensus as to
how it should be done. For example, in Postgres and Sqlite the `RETURNING` clause is used to return the
arbitrary values from the inserted row. In SQL Server, the `OUTPUT` clause is used to achieve
a similar functionality. Other databases like H2 do not support `query.returning` at all.

In situations where you cannot use the `query.returning(...)` call (e.g. H2) you can use the
`query.returningKeys(...)` call. This will attempt to get the generated keys from the inserted row
by using low-level driver API calls such as the `PreparedStatement.getGeneratedKeys()` function for JDBC.
Again, how these low-level API calls behave is also not completely uniform across all databases.
For example, in most databases `returningKeys` is only supported for INSERT queries. However in H2,
you can also do it in UPDATE queries. Additionally, when using `returningKeys` most databases will only 
let you return the key of the inserted row that is being generated however, H2 will let you return the 
value of any column in it.
 
Bottom line, returning inserted row IDs is not uniformly supported across all databases. You may need
to try out one or two different approaches to get the desired result.
""".trimIndent()

}
// @formatter:on
