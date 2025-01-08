package io.exoquery

import io.exoquery.xr.NumericOperator
import io.exoquery.xr.UnaryOperator
import io.exoquery.xr.XR
import io.exoquery.xr.XR.UnaryOp
import io.exoquery.xr.XRType
import io.exoquery.xr.decodeXR
import io.exoquery.xr.encode
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.Matcher
import io.kotest.matchers.be
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

infix fun <T: XR, U : T> T.shouldBeXR(expected: U): T {
  this should be(expected)
  // Normally XR equality doesn't care about the type but for serialization tests
  // we want to explicitly check it
  this.type should be(expected.type)
  // same with the xr location
  this.loc should be(expected.loc)
  return this
}

class EncodingSpecXR: FreeSpec({
  "XR.Expression" - {
    "Ident" {
      val xr = XR.Ident("foo", XRType.Value)
      xr.encode().decodeXR() shouldBeXR xr
    }
    "Const" - {
      "Int" { XR.Const.Boolean(true).let { xr -> xr.encode().decodeXR() shouldBeXR xr } }
      "Boolean" { XR.Const.Int(1).let { xr -> xr.encode().decodeXR() shouldBeXR xr } }
      "Char" { XR.Const.Char('a').let { xr -> xr.encode().decodeXR() shouldBeXR xr } }
      "Byte" { XR.Const.Byte(1).let { xr -> xr.encode().decodeXR() shouldBeXR xr } }
      "Short" { XR.Const.Short(1).let { xr -> xr.encode().decodeXR() shouldBeXR xr } }
      "Long" { XR.Const.Long(1).let { xr -> xr.encode().decodeXR() shouldBeXR xr } }
      "String" { XR.Const.String("foo").let { xr -> xr.encode().decodeXR() shouldBeXR xr } }
      "Float" { XR.Const.Float(1.0f).let { xr -> xr.encode().decodeXR() shouldBeXR xr } }
      "Double" { XR.Const.Double(1.0).let { xr -> xr.encode().decodeXR() shouldBeXR xr } }
      "Null" { XR.Const.Null().let { xr -> xr.encode().decodeXR() shouldBeXR xr } }
    }
    "BinaryOp" {
      val one = XR.Const.Int(1)
      val two = XR.Const.Int(2)
      XR.BinaryOp(one, NumericOperator.plus, two).let { xr -> xr.encode().decodeXR() shouldBeXR xr }
      XR.BinaryOp(one, NumericOperator.minus, two).let { xr -> xr.encode().decodeXR() shouldBeXR xr }
      XR.BinaryOp(one, NumericOperator.mult, two).let { xr -> xr.encode().decodeXR() shouldBeXR xr }
      XR.BinaryOp(one, NumericOperator.div, two).let { xr -> xr.encode().decodeXR() shouldBeXR xr }
    }
    "UnaryOp" {
      val one = XR.Const.Int(1)
      XR.UnaryOp(NumericOperator.minus, one).let { xr -> xr.encode().decodeXR() shouldBeXR xr }
    }
    "TagForParam" {
      val xr = XR.TagForParam(BID.new(), XRType.Value)
      xr.encode().decodeXR() shouldBeXR xr
    }
    "TagForSqlExpression" {
      val xr = XR.TagForSqlExpression(BID.new(), XRType.Value)
      xr.encode().decodeXR() shouldBeXR xr
    }
  }
})