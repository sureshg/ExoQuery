package io.exoquery.sample

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.Connection

// Define the Users table
object Users : IntIdTable() {
  val name = varchar("name", 50)
  val email = varchar("email", 100).uniqueIndex()
  val age = integer("age")
}

fun databaseOps(emb: EmbeddedPostgres) {
  // Database connection
  val db = Database.connect(emb.postgresDatabase)

  transaction(db) {
    // Ensure tables exist
    SchemaUtils.create(Users)

    // Insert sample data
    val userId = Users.insertAndGetId {
      it[name] = "John Doe"
      it[email] = "john.doe@example.com"
      it[age] = 25
    }


    println("Inserted user with ID: $userId")

    // Custom query using SQL DSL
    val sql =
      Users.select(
        Users.name,
        Case().When(Users.age greater 18, stringLiteral("CAN_DRIVE"))
          .Else(stringLiteral("CANNOT_DRIVE"))
          .alias("can_drive")
      ) .prepareSQL(QueryBuilder(false))

    println(sql)
  }
}

fun main() {
  EmbeddedPostgres.start().use { embeddedPostgres ->
    databaseOps(embeddedPostgres)
  }


}
