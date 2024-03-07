package io.exoquery

import java.util.*

// A runtime bind IDBindsAcc
data class BID(val value: String) {
  companion object {
    fun new() = BID(UUID.randomUUID().toString())
  }
}