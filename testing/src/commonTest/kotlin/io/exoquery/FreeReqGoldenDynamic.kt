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
      "78d1786a-ba08-4d16-804d-fa50b265da82" to "1", "bb48be1e-fcc0-46c0-9018-b237ae5d4d94" to "Joe", "fa887de1-8cc4-431f-b7a9-23b083a0f28f" to "123"
    ),
    "action with free/dynamic action in free" to cr(
      "beforeStuff() INSERT INTO Person (id, name, age) VALUES ({0:1}, {1:Joe}, {2:123}) afterStuff()",
      "ceb18cf6-3961-4b7e-8428-ec7c3f78de6d" to "1", "d8eb1ed6-18c9-43b2-9db7-47697e0382a8" to "Joe", "2db1bf13-39c5-4f5d-a8eb-9a2837ebacdb" to "123"
    ),
    "action with free/direct action in free" to cr(
      "beforeStuff() INSERT INTO Person (id, name, age) VALUES ({0:1}, {1:Joe}, {2:123}) afterStuff()",
      "1dde6fca-c3c1-4a26-a85a-1d1f611d67e7" to "1", "8195ad08-0c5b-4fd2-8248-d4d9bc7b99c4" to "Joe", "cc83f65a-0b93-447c-bacf-83d90f686406" to "123"
    ),
  )
}
