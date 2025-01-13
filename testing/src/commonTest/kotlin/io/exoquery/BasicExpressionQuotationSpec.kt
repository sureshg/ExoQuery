package io.exoquery

import io.exoquery.xr.`+++`
import io.exoquery.xr.XR
import io.exoquery.xr.XRType
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.equals.shouldBeEqual

class BasicExpressionQuotationSpec : FreeSpec({
  "static cases" - {
    "c0={n0+lift}, c1=c0, c={n1+c1} -> {n1+(n0+lift)}" {
      val cap0 = captureValue { 123 + param(456) }
      cap0.determinizeDynamics() shouldBeEqual SqlExpression(
        XR.Const.Int(123) `+++` XR.TagForParam(BID("0"), XRType.Value, XR.Location.Synth),
        Runtimes.Empty,
        Params(listOf(Param(BID("0"), 456)))
      )

      val cap1 = cap0
      val cap = captureValue { 789 + cap1.use }

      // Note that loc properties are different but those are not used in XR equals functions
      cap.determinizeDynamics() shouldBeEqual SqlExpression(
        XR.Const.Int(789) `+++` (XR.Const.Int(123) `+++` XR.TagForParam(BID("0"), XRType.Value, XR.Location.Synth)),
        Runtimes.Empty,
        Params(listOf(Param(BID("0"), 456)))
      )
    }
    "c0()={n0+lift}, c={n1+c0()} -> {n1+(n0+lift)}" {
      fun cap0() = captureValue { 123 + param(456) }
      val cap = captureValue { 789 + cap0().use }
      cap.determinizeDynamics() shouldBeEqual SqlExpression(
        XR.Const.Int(789) `+++` (XR.Const.Int(123) `+++` XR.TagForParam(BID("0"), XRType.Value, XR.Location.Synth)),
        Runtimes.Empty,
        Params(listOf(Param(BID("0"), 456)))
      )
    }
    "c0(i)={n0+i}, c={n1+c0(nn)} -> {n1+(n0+nn)}" {
      fun cap0(input: Int) = captureValue { 123 + param(input) }
      val cap = captureValue { 789 + cap0(456).use }
      cap.determinizeDynamics() shouldBeEqual SqlExpression(
        XR.Const.Int(789) `+++` (XR.Const.Int(123) `+++` XR.TagForParam(BID("0"), XRType.Value, XR.Location.Synth)),
        Runtimes.Empty,
        Params(listOf(Param(BID("0"), 456)))
      )
    }

    "cls Foo{c0=nA+lift}, f=Foo, c={nB+f.c0} -> {nB+(nA+lift)}" {
      class Foo {
        val cap0 = captureValue { 456 + param(456) }
      }
      val f = Foo()
      val cap = captureValue { 789 + f.cap0.use }
      cap.determinizeDynamics() shouldBeEqual SqlExpression(
        XR.Const.Int(789) `+++` (XR.Const.Int(456) `+++` XR.TagForParam(BID("0"), XRType.Value)),
        Runtimes.Empty,
        Params(listOf(Param(BID("0"), 456)))
      )
    }
    "cls Foo{c0()=nA}, f=Foo, c={nB+f.c0()} -> {nB+(nA)}" {
      class Foo {
        fun cap0() = captureValue { 456 }
      }
      val f = Foo()
      val cap = captureValue { 789 + f.cap0().use }
      cap.determinizeDynamics() shouldBeEqual SqlExpression(
        XR.Const.Int(789) `+++` XR.Const.Int(456),
        Runtimes.Empty,
        Params.of()
      )
    }
  }
  "dynamic cases" - {
    "c0D=dyn{nA}, c={nB+c0D} -> {nB+T(B0),R={B0,nA}}" {
      val x = true
      fun cap0() =
        if (x)
          captureValue { 123 }
        else
          captureValue { 456 }
      val cap = captureValue { 789 + cap0().use }

      cap.determinizeDynamics() shouldBeEqual SqlExpression(
        XR.Const.Int(789) `+++` XR.TagForSqlExpression(BID("0"), XRType.Value),
        Runtimes.of(BID("0") to SqlExpression<Any>(XR.Const.Int(123), Runtimes.of(), Params.of())),
        Params.of()
      )
    }
    "cls Foo{c0D=dyn{nA+lift}}, f=Foo, c={nB+f.c0D} -> {nB+T(B0),R={B0,nA+lift}}" {
      val v = true
      class Foo {
        val cap0 =
          if (v) captureValue { 456 + param(456) } else captureValue { 456 + param(999) }
      }
      val f = Foo()
      val cap = captureValue { 789 + f.cap0.use }

      println(cap.determinizeDynamics().show())
      cap.determinizeDynamics() shouldBeEqual SqlExpression(
        XR.Const.Int(789) `+++` XR.TagForSqlExpression(BID("1"), XRType.Value),
        Runtimes.of(
          BID("1") to
          SqlExpression<Any>(
            XR.Const.Int(456) `+++` XR.TagForParam(BID("0"), XRType.Value),
            Runtimes.of(),
            Params(listOf(Param(BID("0"), 456)))
          )
        ),
        Params.of()
      )
    }
    "cls Foo{c0D(i)=dyn{nA+lift(i)}}, f=Foo, c={nB+f.c0D(nn)} -> {nB+T(B0),R={B0,nA+lift(nn)}}" {
      val v = true
      class Foo {
        fun cap0(input: Int) =
          if (v) captureValue { 456 + param(input) } else captureValue { 456 + param(999) }
      }
      val f = Foo()
      val cap = captureValue { 789 + f.cap0(456).use }

      cap.determinizeDynamics() shouldBeEqual SqlExpression(
        XR.Const.Int(789) `+++` XR.TagForSqlExpression(BID("1"), XRType.Value),
        Runtimes.of(
          BID("1") to
          SqlExpression<Any>(
            XR.Const.Int(456) `+++` XR.TagForParam(BID("0"), XRType.Value),
            Runtimes.of(),
            Params(listOf(Param(BID("0"), 456)))
          )
        ),
        Params.of()
      )
    }
  }
})
