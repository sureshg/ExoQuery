package io.exoquery

import io.exoquery.xr.NumericOperator
import io.exoquery.xr.XR
import kotlinx.serialization.*
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoBuf.Default.serializersModule

class Test {
}

fun main() {
  val xrExpr: XR.Expression = XR.BinaryOp(XR.Const.Int(1), NumericOperator.plus, XR.Const.Int(2))
  val start = System.currentTimeMillis()
  //val bytes = ProtoBuf.encodeToByteArray(xr)
  //val decode = ProtoBuf.decodeFromByteArray<XR>(bytes)

  val str = ProtoBuf.encodeToHexString(serializersModule.serializer<XR.Expression>(), xrExpr)
  val decode = ProtoBuf.decodeFromHexString<XR.Expression>(str)

  println("Time (${decode.hashCode()}): ${System.currentTimeMillis() - start}\n${decode}")




}