package io.exoquery

import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
data class BID(val value: String) {
  companion object {
    @OptIn(ExperimentalUuidApi::class)
    fun new() = BID(Uuid.random().toString())
  }
}