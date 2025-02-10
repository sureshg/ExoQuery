package io.exoquery.annotation

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

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class Dsl

@Target(AnnotationTarget.FILE)
@Retention(AnnotationRetention.BINARY)
annotation class ExoGoldenTest

@Target(AnnotationTarget.FILE)
@Retention(AnnotationRetention.BINARY)
annotation class TracesEnabled(vararg val traceType: KClass<out TraceType>)

@Target(AnnotationTarget.FILE)
@Retention(AnnotationRetention.BINARY)
annotation class ExoGoldenOverride
