package io.exoquery

import io.exoquery.printing.GoldenResult
import io.exoquery.printing.cr
import io.exoquery.printing.kt

object FreeReqGoldenDynamic: GoldenQueryFile {
  override val queries = mapOf<String, GoldenResult>(
    "static free/simple sql function" to cr(
      "SELECT p.id, p.name, p.age FROM Person p WHERE MyFunction(p.age)"
    ),
    "static free/simple sql function - pure" to cr(
      "SELECT p.id, p.name, p.age FROM Person p WHERE MyFunction(p.age)"
    ),
    "static free/simple sql function - condition" to cr(
      "SELECT p.id, p.name, p.age FROM Person p WHERE MyFunction(p.age)"
    ),
    "static free/simple sql function - condition" to cr(
      "SELECT p.id, p.name, p.age FROM Person p WHERE MyFunction(p.age)"
    ),
    "query with free/static query in free" to cr(
      "beforeStuff() SELECT p.id AS id, p.name AS name, p.age AS age FROM Person p WHERE p.name = 'Joe' afterStuff()"
    ),
    "query with free/dynamic query in free" to cr(
      "beforeStuff() SELECT p.id AS id, p.name AS name, p.age AS age FROM Person p WHERE p.name = 'Joe' afterStuff()"
    ),
    "query with free/direct query in free" to cr(
      "beforeStuff() SELECT p.id AS id, p.name AS name, p.age AS age FROM Person p WHERE p.name = 'Joe' afterStuff()"
    ),
    "action with free/static action in free" to cr(
      "beforeStuff() INSERT INTO Person (id, name, age) VALUES ({0:1}, {1:Joe}, {2:123}) afterStuff()",
      "c4e6d31c-281b-4bf9-9e99-71de693c625c" to "1", "1cce2500-d09d-404c-a16f-60a4aca46699" to "Joe", "de1ce004-2b29-4def-847c-18ad86507602" to "123"
    ),
    "action with free/dynamic action in free" to cr(
      "beforeStuff() INSERT INTO Person (id, name, age) VALUES ({0:1}, {1:Joe}, {2:123}) afterStuff()",
      "899ff847-208c-4118-815d-f13cfe749547" to "1", "2fa96dd8-61d9-49c2-a094-122eb172c31a" to "Joe", "e39a5cb7-4a9b-4801-b0a1-99506eb53d4b" to "123"
    ),
    "action with free/direct action in free" to cr(
      "beforeStuff() INSERT INTO Person (id, name, age) VALUES ({0:1}, {1:Joe}, {2:123}) afterStuff()",
      "01efe814-6366-4503-84e1-2711b30519db" to "1", "f2d7c6bc-3ca3-4251-be31-ea2de16cae35" to "Joe", "7d888dcb-b1c7-4da5-a98d-ebc454152903" to "123"
    ),
  )
}
