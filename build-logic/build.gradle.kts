plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.nexus.publish.plugin)
    implementation(libs.dokka.gradle.plugin)
    // Override the 1.6.1 dependency coming from kotlin-gradle-plugin
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.jackson.module.kotlin)

    // https://github.com/gradle/gradle/issues/15383#issuecomment-779893192
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}
