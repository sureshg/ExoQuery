package io.exoquery.serial

import io.exoquery.ValueWithSerializer
import kotlinx.serialization.ContextualSerializer
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.serializer
import kotlin.reflect.KClass

inline fun <reified T: Any> contextualSerializer(): ParamSerializer<T> =
  ParamSerializer.Custom(ContextualSerializer(T::class), T::class)

inline fun <reified T: Any> customSerializer(serializer: SerializationStrategy<T>): ParamSerializer<T> =
  ParamSerializer.Custom(serializer, T::class)

inline fun <reified T: Any> customValueSerializer(customValue: ValueWithSerializer<T>): ParamSerializer<T> =
  customValue.asParam()

interface ParamSerializer<T: Any> {
  val serializer: SerializationStrategy<T>
  val cls: KClass<T>

  data class Custom<T: Any>(override val serializer: SerializationStrategy<T>, override val cls: KClass<T>) : ParamSerializer<T>


  object LocalDate : ParamSerializer<kotlinx.datetime.LocalDate> {
    override val serializer = ContextualSerializer(kotlinx.datetime.LocalDate::class)
    override val cls = kotlinx.datetime.LocalDate::class
  }
  object LocalTime : ParamSerializer<kotlinx.datetime.LocalTime> {
    override val serializer = ContextualSerializer(kotlinx.datetime.LocalTime::class)
    override val cls = kotlinx.datetime.LocalTime::class
  }
  object LocalDateTime : ParamSerializer<kotlinx.datetime.LocalDateTime> {
    override val serializer = ContextualSerializer(kotlinx.datetime.LocalDateTime::class)
    override val cls = kotlinx.datetime.LocalDateTime::class
  }
  object Instant : ParamSerializer<kotlinx.datetime.Instant> {
    override val serializer = ContextualSerializer(kotlinx.datetime.Instant::class)
    override val cls = kotlinx.datetime.Instant::class
  }

  object String : ParamSerializer<kotlin.String> {
    override val serializer = serializer<kotlin.String>()
    override val cls = kotlin.String::class
  }
  object Char : ParamSerializer<kotlin.Char> {
    override val serializer = serializer<kotlin.Char>()
    override val cls = kotlin.Char::class
  }
  object Int : ParamSerializer<kotlin.Int> {
    override val serializer = serializer<kotlin.Int>()
    override val cls = kotlin.Int::class
  }
  object Long : ParamSerializer<kotlin.Long> {
    override val serializer = serializer<kotlin.Long>()
    override val cls = kotlin.Long::class
  }
  object Short : ParamSerializer<kotlin.Short> {
    override val serializer = serializer<kotlin.Short>()
    override val cls = kotlin.Short::class
  }
  object Byte : ParamSerializer<kotlin.Byte> {
    override val serializer = serializer<kotlin.Byte>()
    override val cls = kotlin.Byte::class
  }
  object Float : ParamSerializer<kotlin.Float> {
    override val serializer = serializer<kotlin.Float>()
    override val cls = kotlin.Float::class
  }
  object Double : ParamSerializer<kotlin.Double> {
    override val serializer = serializer<kotlin.Double>()
    override val cls = kotlin.Double::class
  }
  object Boolean : ParamSerializer<kotlin.Boolean> {
    override val serializer = serializer<kotlin.Boolean>()
    override val cls = kotlin.Boolean::class
  }
}
