package io.exoquery.util

object NumbersToWords {
  private val ZERO = "Zero"
  private val oneToNine = arrayOf<String>(
    //"one", "two", "three", "four", "five", "six", "seven", "eight", "nine"
    //"first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth", "ninth"
    "First", "Second", "Third", "Fourth", "Fifth", "Sixth", "Seventh", "Eighth", "Ninth"
  )

  private val tenToNinteen = arrayOf<String>(
    //"ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen"
    //"tenth", "eleventh", "twelfth", "thirteenth", "fourteenth", "fifteenth", "sixteenth", "seventeenth", "eighteenth", "nineteenth"
    "Tenth", "Eleventh", "Twelfth", "Thirteenth", "Fourteenth", "Fifteenth", "Sixteenth", "Seventeenth", "Eighteenth", "Nineteenth"
  )

  private val dozensTH = arrayOf<String>(
    //"tenth", "twentieth", "thirtieth", "fortieth", "fiftieth", "sixtieth", "seventieth", "eightieth", "ninetieth"
    "Tenth", "Twentieth", "Thirtieth", "Fortieth", "Fiftieth", "Sixtieth", "Seventieth", "Eightieth", "Ninetieth"
  )

  private val dozens = arrayOf<String>(
    //"ten", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"
    "Ten", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"
  )

  operator fun invoke(number: Int): String =
    if (number == 0)
      ZERO
    else {
      // decapitalize the first letter
      generate(number).trim().replaceFirstChar { it.lowercase() }
    }

  fun generate(number: Int): String =
    if (number < 0 || number > 99)
      throw IllegalArgumentException("Number must be between 0 and 99")
    else
      generate1To99(number)

  private fun generate1To99(number: Int): String =
    if (number <= 9 && number > 0)
      oneToNine[number - 1]
    else if (number <= 19)
      tenToNinteen[number % 10]
    else {
      // I.e. whole nubmers "Twentieth", "Thirtieth", "Fortieth", "Fiftieth", "Sixtieth", "Seventieth", "Eightieth", "Ninetieth"
      if (number % 10 == 0)
        dozensTH[number / 10 - 1]
      else
        dozens[number / 10 - 1].toString() + "" + generate1To99(number % 10)
    }
}
