package io.exoquery

import io.exoquery.printing.PrintMisc
import io.exoquery.sql.SqlIdiom
import io.exoquery.sql.Token
import io.exoquery.xr.RuntimeQueryBuilder
import io.exoquery.xr.XR

// TODO value needs to be Token and we need to write a lifter for Token
//      This probably needs to be something like SqlCompiledQuery<T> which has constructors
//      SqlCompiledQuery.compileTime<T>(String|Token,Params,serialier<T>=some-default-value) and SqlCompiledQuery.runtime(query:SqlQuery,serialier<T>=some-default-value)
//      (actually come to think of it, we can probably implement the dynamic path directly and have the staic path replace the build() method if it's possible)
// needsTokenization is a flag indicating whether we need to call token.build to get the query or if we
// can just use the value-string. Typically we cannot use the value-string because there is a paramList (since we don't know how many instances of "?" to use)
// This needs to be checked from the Token at compile-time (also if the dialect requires numbered parameters
// it is also useful to use Token)
data class SqlCompiledQuery<T>(val value: String, val token: Token, val needsTokenization: Boolean, val label: String?)

data class SqlQuery<T>(override val xr: XR.Query, override val runtimes: RuntimeSet, override val params: ParamSet): ContainerOfXR {
  fun determinizeDynamics(): SqlQuery<T> = DeterminizeDynamics().ofQuery(this)

  fun show() = PrintMisc().invoke(this)
  // TODO We can build the dynamic path here directly!... and have the static path replace the logic here
  // TODO this needs to take a Dialect argument that is summoned on the compiler via Class.forName (also do the dynamic path if Class.forName was null and warn the user about that)
  //      (note there there should also be some kind of global setting (or file-annotation) that makes failures happen in the case of the dynamic path)

  /*
  Argument taking the name of a query: (also make buildPretty) to pretty-print it
  query.build<PostgresDialect>(, "GetStuffFromStuff")
  ------< GetStuffFromStuff >-----
  select ...

  (fail if there are duplicate names in a file)

  Add another capability: Annotation on the top of a file to put into different location:
  @file:ExoLocation("src/main/resources/queries")
   */

  fun buildRuntime(dialect: SqlIdiom, label: String?, pretty: Boolean = false): SqlCompiledQuery<T> =
    RuntimeQueryBuilder(this, dialect, label, pretty).invoke()

  fun <Dialect: SqlIdiom> build(): SqlCompiledQuery<T> = TODO()
  fun <Dialect: SqlIdiom> build(label: String): SqlCompiledQuery<T> = TODO()

  fun <Dialect: SqlIdiom> buildPretty(): SqlCompiledQuery<T> = TODO()
  fun <Dialect: SqlIdiom> buildPretty(label: String): SqlCompiledQuery<T> = TODO()
  override fun rebuild(xr: XR, runtimes: RuntimeSet, params: ParamSet): SqlQuery<T> =
    copy(xr = xr as? XR.Query ?: xrError("Failed to rebuild SqlQuery with XR of type ${xr::class} which was: ${xr.show()}"), runtimes = runtimes, params = params)

  override fun withNonStrictEquality(): SqlQuery<T> = copy(params = params.withNonStrictEquality())
}
