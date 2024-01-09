plugins {
    id("publish")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions{
        freeCompilerArgs = listOf("-Xcontext-receivers")
    }
}

dependencies {
    api(kotlin("reflect"))
}