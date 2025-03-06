package io.exoquery

import io.exoquery.printing.PrintMisc
import io.exoquery.sql.SqlIdiom
import io.exoquery.xr.RuntimeQueryBuilder
import io.exoquery.xr.XR

data class SqlQuery<T>(override val xr: XR.Query, override val runtimes: RuntimeSet, override val params: ParamSet): ContainerOfFunXR {
  fun determinizeDynamics(): SqlQuery<T> = DeterminizeDynamics().ofQuery(this)

  // Don't need to do anything special in order to convert runtime, just call a function that the TransformProjectCapture can't see through
  fun dyanmic(): SqlQuery<T> = this

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
