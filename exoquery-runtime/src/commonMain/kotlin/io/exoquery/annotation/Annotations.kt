package io.exoquery.annotation

/**
 * This annotation means that the construct e.g. the SqlQuery represents a value captured during compile-time by the
 * ExoQuery system (via the parser and transformers). It cannot be specified by the user.
 */


@Target(AnnotationTarget.FUNCTION, AnnotationTarget.FIELD, AnnotationTarget.PROPERTY, AnnotationTarget.LOCAL_VARIABLE)
@Retention(AnnotationRetention.BINARY)
annotation class CapturedFunction

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class Dsl

@Target(AnnotationTarget.FILE)
@Retention(AnnotationRetention.BINARY)
annotation class ExoGoldenTest

@Target(AnnotationTarget.FILE)
@Retention(AnnotationRetention.BINARY)
annotation class ExoGoldenOverride
