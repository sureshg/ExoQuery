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

repositories {
    mavenCentral()
    mavenLocal()
}

application {
    mainClass.set("MainKt")
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    implementation("io.exoquery:pprint-kotlin:1.0")
}

tasks.test {
    useJUnitPlatform()
}
