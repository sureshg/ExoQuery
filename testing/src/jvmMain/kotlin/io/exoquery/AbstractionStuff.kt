package io.exoquery

import io.exoquery.annotation.CapturedFunction


fun main() {
  println("helloo")

  data class People(val id: Int, val name: String, val age: Int)
  data class Address(val ownerId: Int, val street: String, val city: String)

  // why does this not work when I use @CapturedFunction? Does the projection not work with scaffolding? need to look into that
  fun peopleAndAddresses() =
    capture.select {
      val p = from(Table<People>())
      val a = join(Table<Address>()) { a -> a.ownerId == p.id }
      p to a
    }

  peopleAndAddresses().build<PostgresDialect>()

















}
