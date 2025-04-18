package io.exoquery

import io.exoquery.sql.PostgresDialect
import io.exoquery.testdata.Person


class ParamReq : GoldenSpecDynamic(ParamReqGoldenDynamic, Mode.ExoGoldenTest(), {
  "single single-param" - {
    val n = "Leah"
    val cap = capture { Table<Person>().filter { p -> p.name == param(n) } }
    val build = cap.build<PostgresDialect>()
    "compileTime" {
      shouldBeGolden(build.value, "Original SQL")
      shouldBeGolden(build.determinizeDynamics(), "Determinized SQL")
      shouldBeGolden(build.determinizeDynamics().params.toString(), "Params")
    }
    val buildRuntime = cap.buildRuntime(PostgresDialect(), null)
    "runtime" {
      shouldBeGolden(buildRuntime.token.build(), "Original SQL")
      shouldBeGolden(buildRuntime.determinizeDynamics().token.build(), "Determinized SQL")
      shouldBeGolden(buildRuntime.determinizeDynamics().params.toString(), "Params")
    }
  }
  "multi single-param" - {
    val n = "Leib"
    val a = 42
    val cap = capture { Table<Person>().filter { p -> p.name == param(n) && p.age == param(a) } }
    val build = cap.build<PostgresDialect>()
    "compileTime" {
      shouldBeGolden(build.value, "Original SQL")
      shouldBeGolden(build.determinizeDynamics().token.build(), "Determinized SQL")
      shouldBeGolden(build.determinizeDynamics().params.toString(), "Params")
    }
    val buildRuntime = cap.buildRuntime(PostgresDialect(), null)
    "runtime" {
      shouldBeGolden(buildRuntime.token.build(), "Original SQL")
      shouldBeGolden(buildRuntime.determinizeDynamics().token.build(), "Determinized SQL")
      shouldBeGolden(buildRuntime.determinizeDynamics().params.toString(), "Params")
    }
  }

  "single multi-param" - {
    val names = listOf("Leah", "Leib")
    val cap = capture { Table<Person>().filter { p -> p.name in params(names) } }
    val build = cap.build<PostgresDialect>()
    "compileTime" {
      shouldBeGolden(build.value, "Original SQL")
      shouldBeGolden(build.determinizeDynamics().token.build(), "Determinized SQL")
      shouldBeGolden(build.determinizeDynamics().params.toString(), "Params")
    }
    val buildRuntime = cap.buildRuntime(PostgresDialect(), null)
    "runtime" {
      shouldBeGolden(buildRuntime.token.build(), "Original SQL")
      shouldBeGolden(buildRuntime.determinizeDynamics().token.build(), "Determinized SQL")
      shouldBeGolden(buildRuntime.determinizeDynamics().params.toString(), "Params")
    }
  }

  "multi multi-param" - {
    val names = listOf("Leah", "Leib")
    val ages = listOf(42, 43)
    val cap = capture { Table<Person>().filter { p -> p.name in params(names) && p.age in params(ages) } }
    val build = cap.build<PostgresDialect>()
    "compileTime" {
      shouldBeGolden(build.value, "Original SQL")
      shouldBeGolden(build.determinizeDynamics().token.build(), "Determinized SQL")
      shouldBeGolden(build.determinizeDynamics().params.toString(), "Params")
    }
    val buildRuntime = cap.buildRuntime(PostgresDialect(), null)
    "runtime" {
      shouldBeGolden(buildRuntime.token.build(), "Original SQL")
      shouldBeGolden(buildRuntime.determinizeDynamics().token.build(), "Determinized SQL")
      shouldBeGolden(buildRuntime.determinizeDynamics().params.toString(), "Params")
    }
  }

  "one single, one multi" - {
    val n = "Joe"
    val ages = listOf(42, 43)
    val cap = capture { Table<Person>().filter { p -> p.name == param(n) && p.age in params(ages) } }
    val build = cap.build<PostgresDialect>()
    "compileTime" {
      shouldBeGolden(build.value, "Original SQL")
      shouldBeGolden(build.determinizeDynamics().token.build(), "Determinized SQL")
      shouldBeGolden(build.determinizeDynamics().params.toString(), "Params")
    }
    val buildRuntime = cap.buildRuntime(PostgresDialect(), null)
    "runtime" {
      shouldBeGolden(buildRuntime.token.build(), "Original SQL")
      shouldBeGolden(buildRuntime.determinizeDynamics().token.build(), "Determinized SQL")
      shouldBeGolden(buildRuntime.determinizeDynamics().params.toString(), "Params")
    }
  }
})
