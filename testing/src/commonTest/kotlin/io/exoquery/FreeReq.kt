package io.exoquery

import io.exoquery.testdata.Person

class FreeReq : GoldenSpecDynamic(GoldenQueryFile.Empty, Mode.ExoGoldenOverride(), {

  // TODO if 'free' not follorwed by anything there should be some kidn of compile error
  "static free" - {
    "simple sql function" {
      shouldBeGolden(capture { Table<Person>().filter { p -> free("MyFunction(${p.age})")<Boolean>() } }.build<PostgresDialect>())
    }
    "simple sql function - pure" {
      shouldBeGolden(capture { Table<Person>().filter { p -> free("MyFunction(${p.age})").asPure<Boolean>() } }.build<PostgresDialect>())
    }
    "simple sql function - condition" {
      shouldBeGolden(capture { Table<Person>().filter { p -> free("MyFunction(${p.age})").asConditon() } }.build<PostgresDialect>())
    }
    "simple sql function - condition" {
      shouldBeGolden(capture { Table<Person>().filter { p -> free("MyFunction(${p.age})").asPureConditon() } }.build<PostgresDialect>())
    }
  }
  "dynamic free" - {

  }

})
