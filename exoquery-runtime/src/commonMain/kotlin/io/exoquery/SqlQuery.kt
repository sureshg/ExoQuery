package io.exoquery

import io.exoquery.printing.PrintMisc
import io.exoquery.sql.SqlIdiom
import io.exoquery.util.formatQuery
import io.exoquery.xr.RuntimeQueryBuilder
import io.exoquery.xr.XR

// TODO value needs to be Token and we need to write a lifter for Token
//      This probably needs to be something like SqlCompiledQuery<T> which has constructors
//      SqlCompiledQuery.compileTime<T>(String|Token,Params,serialier<T>=some-default-value) and SqlCompiledQuery.runtime(query:SqlQuery,serialier<T>=some-default-value)
//      (actually come to think of it, we can probably implement the dynamic path directly and have the staic path replace the build() method if it's possible)
data class SqlCompiledQuery<T>(val value: String, val label: String?)

data class SqlQuery<T>(override val xr: XR.Query, override val runtimes: Runtimes, override val params: Params): ContainerOfXR {
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

  Add another capability: Annotation on the top of a file to put into different location:
  @file:ExoLocation("src/main/resources/queries")
   */

  fun buildRuntime(dialect: SqlIdiom, label: String?, pretty: Boolean = false): SqlCompiledQuery<T> =
    RuntimeQueryBuilder(this, dialect, label, pretty).invoke()

  fun build(dialect: SqlIdiom): SqlCompiledQuery<T> = TODO()
  fun build(dialect: SqlIdiom, label: String): SqlCompiledQuery<T> = TODO()

  fun buildPretty(dialect: SqlIdiom): SqlCompiledQuery<T> = TODO()
  fun buildPretty(dialect: SqlIdiom, label: String): SqlCompiledQuery<T> = TODO()
  override fun rebuild(xr: XR, runtimes: Runtimes, params: Params): SqlQuery<T> =
    copy(xr = xr as? XR.Query ?: xrError("Failed to rebuild SqlQuery with XR of type ${xr::class} which was: ${xr.show()}"), runtimes = runtimes, params = params)
}
