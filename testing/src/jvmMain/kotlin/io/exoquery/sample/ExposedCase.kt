package io.exoquery.sample

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.transactions.transaction

// Define the Users table
object Users: IntIdTable() {
  val name = varchar("name", 50)
  val email = varchar("email", 100).uniqueIndex()
  val age = integer("age")
}

object Addresses: IntIdTable() {
  val ownerId = reference("owner_id", Users)
  val street = varchar("street", 100)
  val city = varchar("city", 50)
}

fun databaseOps(emb: EmbeddedPostgres) {
  // Database connection
  val db = Database.connect(emb.postgresDatabase)

  transaction(db) {
    // Ensure tables exist
    SchemaUtils.create(Users)

    // Custom query using SQL DSL
//    val sql =
//      Users.select(Users.name).where(
//        (Users.name eq stringLiteral("joe"))
//      )

    val u = Users.alias("u")
    val a = Addresses.alias("a")

    val sql =
      u.join(a, JoinType.INNER, Users.id, Addresses.ownerId)
        .select(Users.id, Users.name, Addresses.street)

    val rs = sql.execute(this)
    println(rs)
  }
}

fun main() {
  EmbeddedPostgres.start().use { embeddedPostgres ->
    databaseOps(embeddedPostgres)
  }


}
