package io.exoquery

import io.exoquery.GoldenQueryFile
import io.exoquery.printing.QueryFileKotlinMaker
import io.exoquery.sql.MirrorIdiom
import io.exoquery.xr.`+==+`
import io.exoquery.xr.XR
import io.kotest.core.spec.AfterSpec

class MirrorContextSpec: GoldenSpecDynamic(MirrorContextSpecGolden, Mode.ExoGoldenTest(), {
  "XR.Expression" - {
    "XR.Ident" {
      shouldBeGolden(XR.Ident("foo"))
    }
    "XR.Const.Int" {
      shouldBeGolden(XR.Const.Int(42))
    }
    "XR.Property" {
      shouldBeGolden(XR.Property(XR.Ident("foo"), "bar"))
    }
    "XR.Property Nested" {
      shouldBeGolden(XR.Property(XR.Property(XR.Ident("foo"), "bar"), "baz"))
    }
    "XR.When 1-Branch" {
      shouldBeGolden(XR.When(listOf(XR.Branch(XR.Ident("foo"), XR.Ident("bar"))), XR.Ident("baz")))
    }
    //"XR.When 1-Branch BinaryOp" {
    //  shouldBeGolden(XR.When(listOf(XR.Branch(XR.Ident("a") `+==+` XR.Ident("b"), XR.Ident("bar"))), XR.Ident("baz")))
    //}
    "XR.When 2-Branch" {
      shouldBeGolden(XR.When(listOf(XR.Branch(XR.Ident("foo"), XR.Ident("bar")), XR.Branch(XR.Ident("baz"), XR.Ident("qux"))), XR.Ident("quux")))
    }
  }
})
