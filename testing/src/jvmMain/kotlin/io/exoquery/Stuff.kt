package io.exoquery

import io.exoquery.comapre.Compare
import io.exoquery.comapre.PrintDiff
import io.exoquery.comapre.show
import io.exoquery.util.NumbersToWords

// Note that "Build" -> "Rebuild" will work for this file because it is "JVM" specifically. It will not work for stuff in commonMain/Test
// in general the "Build" -> "Rebuild" only works for platform specific targets
fun main() { //hello
  (1..100).forEach {
    println(NumbersToWords(it))
  }


}
