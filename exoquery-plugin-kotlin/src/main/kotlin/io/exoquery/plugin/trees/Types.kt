package io.exoquery.plugin.trees

import io.exoquery.SqliteDialect
import io.exoquery.parseError
import io.exoquery.plugin.transform.CX
import io.exoquery.plugin.typeOfClass
import org.jetbrains.kotlin.ir.types.IrType

object Types {
  private var sqlDialectValue: IrType? = null

  context(CX.Scope, CX.Symbology)
  fun sqliteDialect(): IrType =
    sqlDialectValue ?: run {
      val tpe = typeOfClass<SqliteDialect>() ?: parseError("No type for SQLiteDialect found, this is a bug in the plugin")
      sqlDialectValue = tpe
      sqlDialectValue!!
    }
}
