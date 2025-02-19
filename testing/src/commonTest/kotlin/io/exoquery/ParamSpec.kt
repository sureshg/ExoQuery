package io.exoquery

import io.exoquery.serial.ParamSerializer
import io.exoquery.serial.contextualSerializer
import io.exoquery.testdata.Person
import io.exoquery.testdata.*
import io.exoquery.xr.OP
import io.exoquery.xr.XR
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.serialization.ContextualSerializer
import kotlinx.serialization.serializer

// TODO need to test all the kinds of params on the IR level
// TODO then need to implement `params` calls that have custom tokenization
// TODO to think about, DSL needs to at least support let-functions, maybe also run
class ParamSpec : FreeSpec({

  "fullQuery" {
    val cap = capture { Table<Person>().filter { p -> p.name == param("name") } }
    cap.determinizeDynamics() shouldBe SqlQuery(
      XR.Filter(personEnt, pIdent, XR.BinaryOp(XR.Property(pIdent, "name"), OP.`==`, XR.TagForParam.valueTag("0"))),
      RuntimeSet.Empty,
      ParamSet(listOf(Param(BID("0"), "name", ParamSerializer.String)))
    )
  }
  "param" - {
    "string" {
      captureValue { param("name") }.determinizeDynamics() shouldBe SqlExpression(
        XR.TagForParam.valueTag("0"),
        RuntimeSet.Empty,
        ParamSet(listOf(Param(BID("0"), "name", ParamSerializer.String)))
      )
    }
    "int" {
      captureValue { param(123) }.determinizeDynamics() shouldBe SqlExpression(
        XR.TagForParam.valueTag("0"),
        RuntimeSet.Empty,
        ParamSet(listOf(Param(BID("0"), 123, ParamSerializer.Int)))
      )
    }
  }

  "paramCtx" - {
    "date" {
      captureValue { paramCtx(LocalDate(2022,1,1)) }.determinizeDynamics().withNonStrictEquality() shouldBe SqlExpression<LocalDate>(
        XR.TagForParam.valueTag("0"),
        RuntimeSet.Empty,
        ParamSet(listOf(Param(BID("0"), LocalDate(2022,1,1), contextualSerializer<kotlinx.datetime.LocalDate>())))
      ).withNonStrictEquality()
    }
  }

  "paramCustom" - {
    "date" {
      captureValue { paramCustom(LocalDate(2022,1,1), ContextualSerializer(LocalDate::class)) }.determinizeDynamics().withNonStrictEquality() shouldBe SqlExpression<LocalDate>(
        XR.TagForParam.valueTag("0"),
        RuntimeSet.Empty,
        ParamSet(listOf(Param(BID("0"), LocalDate(2022,1,1), contextualSerializer<kotlinx.datetime.LocalDate>())))
      ).withNonStrictEquality()
    }
  }

  "paramCustomValue" - {
    "date" {
      val v = ValueWithSerializer.invoke(LocalDate(2022,1,1), ContextualSerializer(LocalDate::class))
      captureValue { param(v) }.determinizeDynamics().withNonStrictEquality() shouldBe SqlExpression<LocalDate>(
        XR.TagForParam.valueTag("0"),
        RuntimeSet.Empty,
        ParamSet(listOf(Param(BID("0"), LocalDate(2022,1,1), contextualSerializer<LocalDate>())))
      ).withNonStrictEquality()
    }
  }
})
