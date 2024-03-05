package io.exoquery

data class TestEntity(val s: String, val i: Int, val l: Long, val o: Int, val b: Boolean)
data class TestEntity2(val s: String, val i: Int, val l: Long, val o: Int, val b: Boolean)

val qr1 = Table<TestEntity>()
val qr2 = Table<TestEntity2>()