package io.exoquery.xr

import io.exoquery.sql.SqlQueryModel
import io.exoquery.xr.EncodingXR.protoBuf
import kotlinx.serialization.decodeFromHexString
import kotlinx.serialization.encodeToHexString
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.serializer

object EncodingXR {
  val module = SerializersModule {
    polymorphic(XR.CustomQuery::class) {
      subclass(SelectClause::class, SelectClause.serializer())
    }
  }
  val protoBuf = ProtoBuf { serializersModule = module }
}

fun XR.encode(): String {
  return protoBuf.encodeToHexString(EncodingXR.module.serializer<XR>(), this)
}

fun SqlQueryModel.encode(): String {
  return protoBuf.encodeToHexString(EncodingXR.module.serializer<SqlQueryModel>(), this)
}

fun String.decodeXR(): XR {
  return protoBuf.decodeFromHexString(EncodingXR.module.serializer<XR>(), this)
}

fun String.decodeXRExpr(): XR.Expression {
  return protoBuf.decodeFromHexString(EncodingXR.module.serializer<XR.Expression>(), this)
}

fun XRType.encode(): String {
  return protoBuf.encodeToHexString(EncodingXR.module.serializer<XRType>(), this)
}

fun String.decodeXRType(): XRType {
  return protoBuf.decodeFromHexString(EncodingXR.module.serializer<XRType>(), this)
}
