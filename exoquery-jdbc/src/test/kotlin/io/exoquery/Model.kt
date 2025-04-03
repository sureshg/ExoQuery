package io.exoquery

import kotlinx.serialization.Serializable

@Serializable
data class Person(val id: Int, val firstName: String, val lastName: String, val age: Int)

@Serializable
data class Address(val ownerId: Int, val street: String, val zip: String)
