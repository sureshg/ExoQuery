package io.exoquery

import io.exoquery.xr.NumericOperator
import io.exoquery.xr.XR
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf

class Test {
}

fun main() {
  val xr: XR = XR.BinaryOp(XR.Const.Int(1), NumericOperator.plus, XR.Const.Int(2))
  for (i in 0..1000) {
    val start = System.currentTimeMillis()
    val bytes = ProtoBuf.encodeToByteArray(xr)
    val decode = ProtoBuf.decodeFromByteArray<XR>(bytes)
    println("Time (${decode.hashCode()}): ${System.currentTimeMillis() - start}")
  }



}