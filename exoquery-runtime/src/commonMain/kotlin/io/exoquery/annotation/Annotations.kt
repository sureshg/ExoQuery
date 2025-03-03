package io.exoquery.annotation

import io.exoquery.serial.ParamSerializer
import io.exoquery.util.TraceType
import kotlin.reflect.KClass

/**
 * This annotation means that the construct e.g. the SqlQuery represents a value captured during compile-time by the
 * ExoQuery system (via the parser and transformers). It cannot be specified by the user.
 */


@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class CapturedFunction

@Target(AnnotationTarget.TYPE, AnnotationTarget.FUNCTION, AnnotationTarget.FIELD, AnnotationTarget.PROPERTY, AnnotationTarget.LOCAL_VARIABLE)
@Retention(AnnotationRetention.BINARY)
annotation class CapturedDynamic

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS, AnnotationTarget.FIELD, AnnotationTarget.PROPERTY, AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.BINARY)
annotation class Dsl

@Target(AnnotationTarget.FILE)
@Retention(AnnotationRetention.BINARY)
annotation class ExoGoldenTest

// TODO annotate build and other build functions with this. (maybe even have a BuildPretty?). Make a warning
//      if a build function is called from a capture function that this is invalid
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class ExoBuildFunction

@Target(AnnotationTarget.FILE, AnnotationTarget.TYPE)
@Retention(AnnotationRetention.BINARY)
annotation class TracesEnabled(vararg val traceType: KClass<out TraceType>)

@Target(AnnotationTarget.FILE)
@Retention(AnnotationRetention.BINARY)
annotation class ExoGoldenOverride

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class ExoExtras

sealed interface DslFunctionCallType {
  object PureFunction : DslFunctionCallType
  object ImpureFunction : DslFunctionCallType
  object Aggregator : DslFunctionCallType
  object QueryAggregator : DslFunctionCallType
}

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class DslFunctionCall(val type: KClass<out DslFunctionCallType>)

@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY, AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.BINARY)
annotation class DslNestingIgnore

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class ParamStatic(val type: KClass<out ParamSerializer<out Any>>)

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class ParamCtx

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class ParamPrimitive

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class ParamCustom

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class ParamCustomValue

/**
 * Used to annotate a type so that the ExoQuery system knows that it is a value (i.e. a value-XRType)
 * that needs to be encoded/decoded itself and not further broken down into its components during
 * select-query expasion. For example given something like this:
 * ```
 * data class MyDate(val year: Int, val month: Int, val day: Int)
 * data class Customer(name: String, lastOrder: MyDate)
 * capture { Table<Customer>() }
 * // Would be broken down into something like:
 * // SELECT name, lastOrder_year, lastOrder_month, lastOrder_day FROM Customer
 * // However, if we annotate MyDate with ExoValue i.e. data class `Customer(name: String, lastOrder: @ExoValue MyDate)`
 * // then the query will be:
 * // SELECT name, lastOrder FROM Customer
 * ```
 * During deserialization the system will expect to have a serializer dynamcially configured for MyDate (NOT an encoder
 * since ExoValue does not imply the value in encoding is contextual). In order to both mark the property as a ExoQuery value
 * and mark it as Contextual (telling the system to expect a direct decoder for MyValue) annotate the type as @Contextual instead
 * i.e. `data class Customer(name: String, lastOrder: @Contextual MyDate)`
 */
@Target(AnnotationTarget.TYPE)
@Retention(AnnotationRetention.BINARY)
annotation class ExoValue

// Using annotations to identify what functions capture queries/expressions/etc...
// instead of parsing them by name is a more flexible approach.
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class ExoCapture

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class ExoCaptureSelect

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class ExoCaptureExpression

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.BINARY)
annotation class ExoUseExpression
