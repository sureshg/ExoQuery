package io.exoquery.comapre

import io.exoquery.elaborateDataClass

// TODO need select-query spec for nested classes

object ElaborationScratch {
  data class Name(val first: String?, val last: String)
  data class PersonNestedNullable1(val name: Name?, val age: Int)

  data class FullName(val name: Name?, val title: String)
  data class PersonNestedNullable2(val fullName: FullName?, val age: Int)

  fun run() {
    println(elaborateDataClass(PersonNestedNullable1(null, 30)))

    println(elaborateDataClass(PersonNestedNullable2(null, 30)))
    println(elaborateDataClass(PersonNestedNullable2(FullName(null, "Sr."), 30)))
    println(elaborateDataClass(PersonNestedNullable2(FullName(Name(null, "Bloggs"), "Sr."), 30)))
  }
}


fun main() {
  ElaborationScratch.run()
}
