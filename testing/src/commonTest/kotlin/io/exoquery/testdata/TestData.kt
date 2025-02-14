package io.exoquery.testdata

import io.exoquery.capture
import io.exoquery.xr.XR
import io.exoquery.xr.XRType

data class Person(val id: Int, val name: String, val age: Int)
data class Address(val ownerId: Int, val street: String, val city: String)

val joes = capture { Table<Person>().filter { p -> p.name == "Joe" } }
val personTpe = XRType.Product("Person", listOf("id" to XRType.Value, "name" to XRType.Value, "age" to XRType.Value))
val addressTpe = XRType.Product("Address", listOf("ownerId" to XRType.Value, "street" to XRType.Value, "city" to XRType.Value))
val personEnt = XR.Entity("Person", personTpe)
val addressEnt = XR.Entity("Address", addressTpe)
val pIdent = XR.Ident("p", personTpe)
val aIdent = XR.Ident("a", addressTpe)
