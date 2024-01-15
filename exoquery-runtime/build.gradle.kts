plugins {
    id("publish")
    id("com.google.devtools.ksp") version "1.8.20-1.0.11"
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions{
        freeCompilerArgs = listOf("-Xcontext-receivers")
    }
}

kotlin.sourceSets.main {
    kotlin.srcDirs(
        file("$buildDir/generated/ksp/main/kotlin"),
    )
}

ksp {
    arg("matchableName", "Mat")
    arg("componentName", "Slot")
}

dependencies {
    api(kotlin("reflect"))
    testImplementation("io.kotest:kotest-runner-junit5:5.8.0")
    ksp("io.exoquery:decomat-ksp:0.0.4")
    implementation("io.exoquery:decomat-core:0.0.4")
}

// Needed for Kotest
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}