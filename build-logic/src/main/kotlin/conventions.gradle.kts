import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

repositories {
    mavenLocal()
    mavenCentral()
}

plugins {
    kotlin("jvm")

    id("com.palantir.git-version")
}

val gitVersion: groovy.lang.Closure<*> by extra

group = "io.exoquery"
version = gitVersion()

check("$version".isNotBlank() && version != "unspecified")
    { "invalid version $version" }

java {
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "11"
    }
}