package io.exoquery

import io.exoquery.controller.jdbc.DatabaseController
import io.exoquery.controller.jdbc.fromConfig
import io.exoquery.controller.runActions
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlin.test.Test



fun main() {
  val emb = EmbeddedPostgres.start()
  val ds = emb.postgresDatabase
  val query = capture {
    Table<Person>().filter { p -> p.lastName == "Ioffe" }
  }

  runBlocking {

    val postgres = DatabaseController.Postgres(ds)
    postgres.runActions(
      """
      CREATE TABLE Person (
        id SERIAL PRIMARY KEY,
        firstName VARCHAR(255),
        lastName VARCHAR(255),
        age INT
      );
      INSERT INTO Person (firstName, lastName, age) VALUES
        ('Leah', 'Ioffe', 9),
        ('Leib', 'Ioffe', 7),
        ('Maria', 'Ioffe', 37)
      """.trimIndent()
    )

    val output = query.build<PostgresDialect>().runOn(postgres)
    println(output)
  }
}

class MyTestRunner {
  @Test
  fun test() {
    main()
  }
}
