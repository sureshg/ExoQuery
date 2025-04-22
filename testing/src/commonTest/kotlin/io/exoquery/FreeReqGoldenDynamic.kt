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
      "8b36e82d-c572-42d4-9649-916a4f9d5fa7" to "1", "480b3b5c-21de-4aee-82e2-239bf55ec747" to "Joe", "e958fdd1-527c-4e63-804d-76424b37f2d1" to "123"
    ),
    "action with free/dynamic action in free" to cr(
      "beforeStuff() INSERT INTO Person (id, name, age) VALUES ({0:1}, {1:Joe}, {2:123}) afterStuff()",
      "d81c0c82-f566-4b82-a4ab-b6237df79f42" to "1", "6b454f23-f559-4c85-9e9a-e038adecd8d0" to "Joe", "12abbcc9-87ff-47a4-8c7d-6b98424b98f2" to "123"
    ),
    "action with free/direct action in free" to cr(
      "beforeStuff() INSERT INTO Person (id, name, age) VALUES ({0:1}, {1:Joe}, {2:123}) afterStuff()",
      "02622ec9-71c4-4de5-8b6f-3c09938e02b5" to "1", "fde02390-f21a-4309-b596-d0f9d6925203" to "Joe", "71477a28-117b-452e-aafa-09b922880700" to "123"
    ),
  )
}
