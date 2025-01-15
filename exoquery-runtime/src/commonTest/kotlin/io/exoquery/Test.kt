//package io.exoquery
//
//import io.exoquery.xr.NumericOperator
//import io.exoquery.xr.XR
//import kotlinx.serialization.*
//import kotlinx.serialization.protobuf.ProtoBuf
//import kotlinx.serialization.protobuf.ProtoBuf.Default.serializersModule
//import kotlin.time.measureTime
//import kotlin.time.measureTimedValue
//
//class Test {
//}
//
//fun main() {
//  val xrExpr: XR.Expression = XR.BinaryOp(XR.Const.Int(1), NumericOperator.plus, XR.Const.Int(2))
//
//  //val bytes = ProtoBuf.encodeToByteArray(xr)
//  //val decode = ProtoBuf.decodeFromByteArray<XR>(bytes)
//
//  val (decode, timeTaken) = measureTimedValue {
//    val str = ProtoBuf.encodeToHexString(serializersModule.serializer<XR.Expression>(), xrExpr)
//    val decode = ProtoBuf.decodeFromHexString<XR.Expression>(str)
//    decode
//  }
//
//  println("Time (${decode.hashCode()}): ${timeTaken}\n${decode}")
//
//
//
//
//}