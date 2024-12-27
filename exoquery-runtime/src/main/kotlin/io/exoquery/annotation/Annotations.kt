package io.exoquery.annotation

@Target(AnnotationTarget.TYPE)
@Retention(AnnotationRetention.BINARY)
annotation class Captured(val value: String = "")
