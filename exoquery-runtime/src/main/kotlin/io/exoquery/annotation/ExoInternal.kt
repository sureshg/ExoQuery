package io.exoquery.annotation

@RequiresOptIn(message = "Only to be used in MyPackage")
@Retention(AnnotationRetention.BINARY)
annotation class ExoInternal

@Retention(AnnotationRetention.BINARY)
annotation class ExoMethod(val name: String)

@Retention(AnnotationRetention.BINARY)
annotation class ParseXR
