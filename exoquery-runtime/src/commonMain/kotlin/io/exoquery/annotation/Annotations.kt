package io.exoquery.annotation

@Target(AnnotationTarget.TYPE)
@Retention(AnnotationRetention.BINARY)
annotation class Captured

@Target(AnnotationTarget.FILE)
@Retention(AnnotationRetention.BINARY)
annotation class ExoGoldenTest

@Target(AnnotationTarget.FILE)
@Retention(AnnotationRetention.BINARY)
annotation class ExoGoldenOverride
