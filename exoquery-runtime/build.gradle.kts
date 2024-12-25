plugins {
    id("publish")
    id("com.google.devtools.ksp") version "2.0.0-1.0.24"
    id("io.exoquery.terpal-plugin") version "2.0.0-1.0.0.PL"
}

kotlin {
    jvmToolchain(11)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions{
        freeCompilerArgs = listOf("-Xcontext-receivers")
        // Otherwise will have: Could not resolve io.exoquery:pprint-kotlin:2.0.1.
        // Incompatible because this component declares a component, compatible with Java 11 and the consumer needed a component, compatible with Java 8
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

kotlin.sourceSets.main {
    kotlin.srcDirs(
        file("$buildDir/generated/ksp/main/kotlin"),
    )
}

repositories {
    mavenCentral()
    mavenLocal()
}

ksp {
    arg("matchableName", "Mat")
    arg("componentName", "Slot")
    arg("middleComponentName", "MSlot")
    arg("constructorComponentName", "CS")
    arg("fromHereFunctionName", "cs")
    arg("fromFunctionName", "csf")
    arg("renderAdtFunctions", "true")
    arg("renderFromHereFunction", "true")
    java {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    api(kotlin("reflect"))
    testImplementation("io.kotest:kotest-runner-junit5:5.8.0")
    // Actually this is going to be 0.0.5 - using an unpublished one now
    ksp("io.exoquery:decomat-ksp:0.3.0")
    implementation("io.exoquery:pprint-kotlin:2.0.1")
    // Actually this is going to be 0.0.5 - using an unpublished one now
    api("io.exoquery:decomat-core:0.3.0")
    implementation("com.facebook:ktfmt:0.43")
    api("io.exoquery:terpal-runtime:1.0.6")
    api("com.github.vertical-blank:sql-formatter:2.0.4")
}

// Needed for Kotest
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}