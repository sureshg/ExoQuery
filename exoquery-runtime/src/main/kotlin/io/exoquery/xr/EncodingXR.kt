package io.exoquery.xr

import kotlinx.serialization.encodeToHexString
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoBuf.Default.serializersModule
import kotlinx.serialization.serializer

fun XR.Expression.encode(): String {
  return ProtoBuf.encodeToHexString(serializersModule.serializer<XR.Expression>(), this)
}