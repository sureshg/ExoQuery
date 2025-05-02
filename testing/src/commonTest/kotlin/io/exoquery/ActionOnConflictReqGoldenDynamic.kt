package io.exoquery

import io.exoquery.printing.GoldenResult
import io.exoquery.printing.cr
import io.exoquery.printing.kt

object ActionOnConflictReqGoldenDynamic: GoldenQueryFile {
  override val queries = mapOf<String, GoldenResult>(
    "onConflictUpdate - complex/XR" to kt(
      "insert<Person> { set(thisinsert.name to Joe, thisinsert.age to 123) }.onConflictUpdate(listOf(thishidden.id))(listOf(thishidden.name to x.name + excluding.name, thishidden.age to x.age + excluding.age))"
    ),
    "onConflictUpdate - complex/SQL" to cr(
      "INSERT INTO Person AS x (name, age) VALUES ('Joe', 123) ON CONFLICT (id) DO UPDATE SET name = (x.name || EXCLUDED.name), age = (x.age + EXCLUDED.age)"
    ),
    "onConflictUpdate - complex + returning/XR" to kt(
      "insert<Person> { set(thisinsert.name to Joe, thisinsert.age to 123) }.onConflictUpdate(listOf(thishidden.id))(listOf(thishidden.name to x.name + excluding.name, thishidden.age to x.age + excluding.age)).returning { p -> p.id }"
    ),
    "onConflictUpdate - complex + returning/SQL" to cr(
      "INSERT INTO Person AS x (name, age) VALUES ('Joe', 123) ON CONFLICT (id) DO UPDATE SET name = (x.name || EXCLUDED.name), age = (x.age + EXCLUDED.age) RETURNING id"
    ),
  )
}
