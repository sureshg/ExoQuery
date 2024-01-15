plugins {
    id("io.exoquery.exoquery-plugin")

    kotlin("jvm")

    application
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions{
        freeCompilerArgs = listOf("-Xcontext-receivers")
    }
}

// Needed for Kotest
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

repositories {
    mavenCentral()
    mavenLocal()
}

application {
    mainClass.set("MainKt")
}

dependencies {
    testImplementation("io.kotest:kotest-runner-junit5:5.8.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    implementation("io.exoquery:pprint-kotlin:1.0")
}

