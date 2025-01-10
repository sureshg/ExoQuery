package io.exoquery

import io.exoquery.xr.`+==+`
import io.exoquery.xr.AggregationOperator
import io.exoquery.xr.NumericOperator
import io.exoquery.xr.XR
import io.exoquery.xr.XRType
import io.exoquery.xr.decodeXR
import io.exoquery.xr.encode
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.be
import io.kotest.matchers.should

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
    "Property" {
      val xr = XR.Property(XR.Ident("foo", XRType.Value), "bar")
      xr.encode().decodeXR() shouldBeXR xr
    }
    "Function1" {
      val xr = XR.Function1(XR.Ident("foo", XRType.Value), XR.Ident("bar", XRType.Value))
      xr.encode().decodeXR() shouldBeXR xr
    }
    "FunctionN" {
      val xr = XR.FunctionN(listOf(XR.Ident("foo", XRType.Value)), XR.Ident("bar", XRType.Value))
      xr.encode().decodeXR() shouldBeXR xr
    }
    "FunctionApply" {
      val xr = XR.FunctionApply(XR.Ident("foo", XRType.Value), listOf(XR.Ident("bar", XRType.Value)))
      xr.encode().decodeXR() shouldBeXR xr
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
    "Aggregation" {
      val xr = XR.Aggregation(AggregationOperator.max, XR.Ident("foo", XRType.Value))
      xr.encode().decodeXR() shouldBeXR xr
    }
    "MethodCall" {
      val callName = XR.MethodCallName(XR.FqName("foo", "bar"), XR.FqName("baz", "qux"))
      val xr = XR.MethodCall(XR.Ident("one", XRType.Value), callName, listOf(XR.Ident("two", XRType.Value)), XRType.Value)
      xr.encode().decodeXR() shouldBeXR xr
    }
    "GlobalCall" {
      val xr = XR.GlobalCall(XR.FqName("foo", "bar"), listOf(XR.Ident("bar", XRType.Value)), XRType.Value)
      xr.encode().decodeXR() shouldBeXR xr
    }
    "Block" {
      val xr = XR.Block(listOf(XR.Variable(XR.Ident("foo"), XR.Ident("bar"))), XR.Ident("baz", XRType.Value))
      xr.encode().decodeXR() shouldBeXR xr
    }
  }
  "XR.Query" - {
    val peopleType = XRType.Product("foo.bar.Product", listOf("name" to XRType.Value))
    val people = XR.Entity("People", peopleType)
    val specialPeople = XR.Entity("SpecialPeople", peopleType)
    val peopleVar = XR.Ident("p", peopleType)
    val greenPeople = XR.Property(peopleVar, "name") `+==+` XR.Const.String("Green")
    val peopleToName = XR.Map(people, peopleVar, XR.Property(XR.Ident("p", peopleType), "name"))

    "FlatMap" {
      val xr = XR.FlatMap(people, XR.Ident("bar", XRType.Value), peopleToName)
      xr.encode().decodeXR() shouldBeXR xr
    }
    "Map" {
      peopleToName.encode().decodeXR() shouldBeXR peopleToName
    }
    "Entity" {
      people.encode().decodeXR() shouldBeXR people
    }
    "Filter" {
      val xr = XR.Filter(people, peopleVar, greenPeople)
      xr.encode().decodeXR() shouldBeXR xr
    }
    "Union" {
      val xr = XR.Union(people, specialPeople)
      xr.encode().decodeXR() shouldBeXR xr
    }
    "FlatJoin" {
      val xr = XR.FlatJoin(XR.JoinType.Inner, people, peopleVar, XR.Ident("something"))
      xr.encode().decodeXR() shouldBeXR xr
    }
    "FlatGroupBy" {
      val xr = XR.FlatGroupBy(XR.Property(peopleVar, "name"))
      xr.encode().decodeXR() shouldBeXR xr
    }
    "FlatSortBy" {
      val xr = XR.FlatSortBy(XR.Property(peopleVar, "name"), XR.Ordering.Asc)
      xr.encode().decodeXR() shouldBeXR xr
    }
    "FlatFilter" {
      val xr = XR.FlatFilter(greenPeople)
      xr.encode().decodeXR() shouldBeXR xr
    }
    "Distinct" {
      val xr = XR.Distinct(people)
      xr.encode().decodeXR() shouldBeXR xr
    }
    "DistinctOn" {
      val xr = XR.DistinctOn(people, peopleVar, XR.Property(peopleVar, "name"))
      xr.encode().decodeXR() shouldBeXR xr
    }
    "Nested" {
      val xr = XR.Nested(people)
      xr.encode().decodeXR() shouldBeXR xr
    }
  }
})