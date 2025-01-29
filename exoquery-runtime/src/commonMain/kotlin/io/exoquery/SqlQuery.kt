package io.exoquery

import io.exoquery.printing.PrintMisc
import io.exoquery.sql.SqlIdiom
import io.exoquery.xr.XR

// TODO value needs to be Token and we need to write a lifter for Token
//      This probably needs to be something like SqlCompiledQuery<T> which has constructors
//      SqlCompiledQuery.compileTime<T>(String|Token,Params,serialier<T>=some-default-value) and SqlCompiledQuery.runtime(query:SqlQuery,serialier<T>=some-default-value)
//      (actually come to think of it, we can probably implement the dynamic path directly and have the staic path replace the build() method if it's possible)
data class SqlCompiledQuery<T>(val value: String)

data class SqlQuery<T>(override val xr: XR.Query, override val runtimes: Runtimes, override val params: Params): ContainerOfXR {
  fun <R> map(f: (T) -> R): SqlQuery<R> = error("The map expression of the Query was not inlined")
  fun <R> flatMap(f: (T) -> SqlQuery<R>): SqlQuery<R> = error("The flatMap expression of the Query was not inlined")
  fun <R> concatMap(f: (T) -> Iterable<R>): SqlQuery<R> = error("The concatMap expression of the Query was not inlined")
  fun <R> filter(f: (T) -> R): SqlQuery<T> = error("The `filter` expression of the Query was not inlined")
  fun <T> union(other: SqlQuery<T>): SqlQuery<T> = error("The `union` expression of the Query was not inlined")
  fun <T> unionAll(other: SqlQuery<T>): SqlQuery<T> = error("The `unionAll` expression of the Query was not inlined")
  fun distinct(): SqlQuery<T> = error("The `distinct` expression of the Query was not inlined")
  fun <R> distinctBy(): SqlQuery<T> = error("The `distinctBy` expression of the Query was not inlined")
  fun nested(): SqlQuery<T> = error("The `nested` expression of the Query was not inlined")
  fun <R> sortedBy(f: (T) -> R): SqlQuery<T> = error("The sort-by expression of the Query was not inlined")
  fun take(f: Int): SqlQuery<T> = error("The take expression of the Query was not inlined")
  fun drop(f: Int): SqlQuery<T> = error("The drop expression of the Query was not inlined")
  fun size(): SqlQuery<Int> = error("The size expression of the Query was not inlined")

  // TODO once Kotlin has property context-recivers, it would be interesting to try adding a Capture context
  //     to each of these. That way the user would legitimately need a `captured` block to invoke them.

  //fun <R> groupBy(f: (T) -> R): GroupedQuery<T> =  error("The groupBy expression of the Query was not inlined")

  // Used in groupBy and various other places to convert query to an expression
  fun value(): SqlExpression<T> = error("The `value` expression of the Query was not inlined")


  fun determinizeDynamics(): SqlQuery<T> = DeterminizeDynamics().ofQuery(this)

  fun show() = PrintMisc().invoke(this)
  // TODO We can build the dynamic path here directly!... and have the static path replace the logic here
  // TODO this needs to take a Dialect argument that is summoned on the compiler via Class.forName (also do the dynamic path if Class.forName was null and warn the user about that)
  //      (note there there should also be some kind of global setting (or file-annotation) that makes failures happen in the case of the dynamic path)

  /*
  Argument taking the name of a query: (also make buildPretty) to pretty-print it
  query.build(PostgresDialect(), "GetStuffFromStuff")
  ------< GetStuffFromStuff >-----
  select ...

  (fail if there are duplicate names in a file)

  Annotation on the top of a file to put into different location:
  @file:ExoLocation("src/main/resources/queries")
  // Or
  @file:ExoGoldenTest which like like "src/main/resources/queries/{packageName}" but ignores file if exists
  // Then build(PostgresDialect(), "...").shouldBeGolden() will test to the labelled query in the corresponding file
  // Then
  @file:ExoGoldenMake which overwrites the file if exists
  // Then you can overwrite the golden file when stuff changed and compare to previous in git
   */
  fun build(dialect: SqlIdiom): SqlCompiledQuery<T> = TODO()
}