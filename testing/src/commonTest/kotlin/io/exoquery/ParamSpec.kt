package io.exoquery

import io.exoquery.serial.ParamSerializer
import io.exoquery.serial.contextualSerializer
import io.exoquery.testdata.Person
import io.exoquery.testdata.*
import io.exoquery.xr.OP
import io.exoquery.xr.XR
import io.exoquery.xr.XRType
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.serialization.ContextualSerializer
import kotlinx.serialization.Serializable

// TODO need to test all the kinds of params on the IR level
// TODO then need to implement `params` calls that have custom tokenization
// TODO to think about, DSL needs to at least support let-functions, maybe also run
class ParamSpec : FreeSpec({

  "fullQuery" {
    val cap = capture { Table<Person>().filter { p -> p.name == param("name") } }
    cap.determinizeDynamics() shouldBe SqlQuery(
      XR.Filter(personEnt, pIdent, XR.BinaryOp(XR.Property(pIdent, "name"), OP.`==`, XR.TagForParam.valueTag("0"))),
      RuntimeSet.Empty,
      ParamSet(listOf(ParamSingle(BID("0"), "name", ParamSerializer.String)))
    )
  }
  "param" - {
    "string" {
      captureValue { param("name") }.determinizeDynamics() shouldBe SqlExpression(
        XR.TagForParam.valueTag("0"),
        RuntimeSet.Empty,
        ParamSet(listOf(ParamSingle(BID("0"), "name", ParamSerializer.String)))
      )
    }
    "int" {
      captureValue { param(123) }.determinizeDynamics() shouldBe SqlExpression(
        XR.TagForParam.valueTag("0"),
        RuntimeSet.Empty,
        ParamSet(listOf(ParamSingle(BID("0"), 123, ParamSerializer.Int)))
      )
    }
  }

  val date = LocalDate(2021, 1, 1)

  @Serializable
  data class MyCustomDate(val year: Int, val month: Int, val day: Int)
  val myDate = MyCustomDate(2021, 1, 1)

  "paramCtx" - {
    "date" {
      captureValue { paramCtx(date) }.determinizeDynamics().withNonStrictEquality() shouldBe SqlExpression<LocalDate>(
        XR.TagForParam.valueTag("0"),
        RuntimeSet.Empty,
        ParamSet(listOf(ParamSingle(BID("0"), date, contextualSerializer<kotlinx.datetime.LocalDate>())))
      ).withNonStrictEquality()
    }
    "myCustomDate" {
      captureValue { paramCtx(myDate) }.determinizeDynamics().withNonStrictEquality() shouldBe SqlExpression<LocalDate>(
        XR.TagForParam.valueTag("0"),
        RuntimeSet.Empty,
        ParamSet(listOf(ParamSingle(BID("0"), myDate, contextualSerializer<MyCustomDate>())))
      ).withNonStrictEquality()
    }
  }

  "paramCustom" - {
    "date" {
      captureValue { paramCustom(date, ContextualSerializer(LocalDate::class)) }.determinizeDynamics().withNonStrictEquality() shouldBe SqlExpression<LocalDate>(
        XR.TagForParam.valueTag("0"),
        RuntimeSet.Empty,
        ParamSet(listOf(ParamSingle(BID("0"), date, contextualSerializer<kotlinx.datetime.LocalDate>())))
      ).withNonStrictEquality()
    }
    "myCustomDate" {
      captureValue { paramCustom(myDate, ContextualSerializer(MyCustomDate::class)) }.determinizeDynamics().withNonStrictEquality() shouldBe SqlExpression<LocalDate>(
        XR.TagForParam.valueTag("0"),
        RuntimeSet.Empty,
        ParamSet(listOf(ParamSingle(BID("0"), myDate, contextualSerializer<MyCustomDate>())))
      ).withNonStrictEquality()
    }
  }

  "paramCustomValue" - {
    "date" {
      val v = ValueWithSerializer.invoke(date, ContextualSerializer(LocalDate::class))
      captureValue { param(v) }.determinizeDynamics().withNonStrictEquality() shouldBe SqlExpression<LocalDate>(
        XR.TagForParam.valueTag("0"),
        RuntimeSet.Empty,
        ParamSet(listOf(ParamSingle(BID("0"), date, contextualSerializer<LocalDate>())))
      ).withNonStrictEquality()
    }
    "myDate" {
      val v = ValueWithSerializer.invoke(myDate, ContextualSerializer(MyCustomDate::class))
      captureValue { param(v) }.determinizeDynamics().withNonStrictEquality() shouldBe SqlExpression<LocalDate>(
        XR.TagForParam.valueTag("0"),
        RuntimeSet.Empty,
        ParamSet(listOf(ParamSingle(BID("0"), myDate, contextualSerializer<MyCustomDate>())))
      ).withNonStrictEquality()
    }
  }

  "params" {
    val myList = listOf(1,2,3)
    captureValue { params(myList) }.determinizeDynamics() shouldBe SqlExpression(
      XR.TagForParam.valueTag("0"),
      RuntimeSet.Empty,
      ParamSet(listOf(ParamMulti(BID("0"), myList, ParamSerializer.Int)))
    )
  }

  "paramsCustom" - {
    "date" {
      val myList = listOf(date, date)
      captureValue { paramsCustom(myList, ContextualSerializer(LocalDate::class)) }.determinizeDynamics().withNonStrictEquality() shouldBe SqlExpression<LocalDate>(
        XR.TagForParam.valueTag("0"),
        RuntimeSet.Empty,
        ParamSet(listOf(ParamMulti(BID("0"), myList, contextualSerializer<LocalDate>())))
      ).withNonStrictEquality()
    }
    "myCustomDate" {
      val myList = listOf(myDate, myDate)
      val cap = captureValue { paramsCustom(myList, ContextualSerializer(MyCustomDate::class)) }


      println("--------------------- Type ee: ---------------\n${cap.xr.type}")

      // TODO need to check this for the other tests as well
      cap.xr.type shouldBe XRType.Value

      cap.determinizeDynamics().withNonStrictEquality() shouldBe SqlExpression<LocalDate>(
        XR.TagForParam.valueTag("0"),
        RuntimeSet.Empty,
        ParamSet(listOf(ParamMulti(BID("0"), myList, contextualSerializer<MyCustomDate>())))
      ).withNonStrictEquality()
    }
  }

  "paramsCustomValue" - {
    "date" {
      val myList = listOf(date, date)
      val v = ValuesWithSerializer.invoke(myList, ContextualSerializer(LocalDate::class))
      captureValue { params(v) }.determinizeDynamics().withNonStrictEquality() shouldBe SqlExpression<LocalDate>(
        XR.TagForParam.valueTag("0"),
        RuntimeSet.Empty,
        ParamSet(listOf(ParamMulti(BID("0"), myList, contextualSerializer<LocalDate>())))
      ).withNonStrictEquality()
    }
    "myCustomDate" {
      val myList = listOf(myDate, myDate)
      val v = ValuesWithSerializer.invoke(myList, ContextualSerializer(MyCustomDate::class))
      captureValue { params(v) }.determinizeDynamics().withNonStrictEquality() shouldBe SqlExpression<LocalDate>(
        XR.TagForParam.valueTag("0"),
        RuntimeSet.Empty,
        ParamSet(listOf(ParamMulti(BID("0"), myList, contextualSerializer<MyCustomDate>())))
      ).withNonStrictEquality()
    }
  }
})
