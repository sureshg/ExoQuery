package io.exoquery.xr

import kotlinx.serialization.decodeFromHexString
import kotlinx.serialization.encodeToHexString
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoBuf.Default.serializersModule
import kotlinx.serialization.serializer

fun XR.encode(): String {
  return ProtoBuf.encodeToHexString(serializersModule.serializer<XR>(), this)
}

fun String.decodeXR(): XR {
  return ProtoBuf.decodeFromHexString(serializersModule.serializer<XR>(), this)
}

fun String.decodeXRExpr(): XR.Expression {
  return ProtoBuf.decodeFromHexString(serializersModule.serializer<XR.Expression>(), this)
}

fun XRType.encode(): String {
  return ProtoBuf.encodeToHexString(serializersModule.serializer<XRType>(), this)
}

fun String.decodeXRType(): XRType {
  return ProtoBuf.decodeFromHexString(serializersModule.serializer<XRType>(), this)
}
