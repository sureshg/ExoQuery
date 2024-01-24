plugins {
    id("publish")

    kotlin("kapt") version "1.8.21"
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions{
        freeCompilerArgs = listOf("-Xcontext-receivers")
    }
}

dependencies {
    implementation("io.exoquery:exoquery-runtime")

    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable")

    kapt("com.google.auto.service:auto-service:1.0.1")
    compileOnly("com.google.auto.service:auto-service-annotations:1.0.1")
    implementation("com.facebook:ktfmt:0.43")

    implementation("io.exoquery:decomat-core:0.0.4")
    implementation("com.tylerthrailkill.helpers:pretty-print:2.0.2")
    implementation("io.exoquery:pprint-kotlin:1.1.0")
    api(kotlin("reflect"))
}
