package io.exoquery.printing

// TODO include locaiton string
data class PrintableValue(val value: String, val type: Type, val queryOutputType: String, val label: String? = null, val params: List<Param> = emptyList()) {
  data class Param(val id: String, val value: String)

  sealed interface Type {
    val interpolatorPrefix: String

    object SqlQuery : Type {
      override val interpolatorPrefix = "cr"
    }

    object KotlinCode : Type {
      override val interpolatorPrefix = "kt"
    }
  }
}
