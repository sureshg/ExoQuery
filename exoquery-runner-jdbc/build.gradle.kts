import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
  // Only uses the base conventions-project because this builds only for jvm and all of those define native targets
  id("conventions")
  kotlin("multiplatform") version "2.2.20"
  id("com.google.devtools.ksp") version "2.2.20-2.0.3"
  alias(libs.plugins.kotest)
  id("io.exoquery.exoquery-plugin")

  // NEED serialization to be able to read the encoded XR, in the future the GradlePlugin should probably add this to the classpath
  kotlin("plugin.serialization") version "2.2.20"
}

version = extra["controllerProjectVersion"].toString()

// Enable logging of wrappers
//tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
//    compilerOptions {
//        freeCompilerArgs.addAll(
//            listOf(
//                "-P",
//                "plugin:io.exoquery.terpal-plugin:traceWrappers=true"
//            )
//        )
//    }
//}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>().configureEach {
  compilerOptions {
    freeCompilerArgs.add("-Xcontext-receivers")
    optIn.add("io.exoquery.annotation.ExoInternal")
    // Otherwise will have: Could not resolve io.exoquery:pprint-kotlin:2.0.1.
    // Incompatible because this component declares a component, compatible with Java 11 and the consumer needed a component, compatible with Java 8
    java {
      sourceCompatibility = JavaVersion.VERSION_11
      targetCompatibility = JavaVersion.VERSION_11
    }
    // If I remove this I get:
    //  'compileJava' task (current target is 11) and 'kaptGenerateStubsKotlin' task (current target is 1.8) jvm target compatibility should be set to the same Java version.
    // Not sure why
    //jvmTarget.set(JvmTarget.JVM_11)
  }
}

repositories {
  mavenCentral()
  mavenLocal()
}

kotlin {
  compilerOptions {
    optIn.add("io.exoquery.annotation.ExoInternal")
  }

  jvmToolchain(17)
  jvm {
  }

  java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  sourceSets {
    jvmMain {
      kotlin.srcDir("src/main/kotlin")
      resources.srcDir("src/main/resources")

      dependencies {
        api(libs.controller.jdbc)
        api(project(":exoquery-runner-core")) // .transaction and other syntaxes come from here

        api(libs.kotlinx.serialization.core)
        api(libs.kotlinx.serialization.protobuf)
        api(libs.kotlinx.coroutines.core)

        // Hikari should be optional on a user-level. The contexts only need a DataSource instance.
        implementation("com.zaxxer:HikariCP:5.0.1")
        implementation("com.typesafe:config:1.4.1")
      }
    }
    jvmTest {
      kotlin.srcDir("src/test/kotlin")
      resources.srcDir("src/test/resources")

      dependencies {
        // This brings in reflection so make sure it is only there in test
        implementation("io.exoquery:pprint-kotlin:3.0.0")
        implementation("io.zonky.test:embedded-postgres:2.0.7")
        implementation("mysql:mysql-connector-java:8.0.29")
        implementation("com.microsoft.sqlserver:mssql-jdbc:7.4.1.jre11")
        implementation("com.h2database:h2:2.2.224")
        implementation("com.oracle.ojdbc:ojdbc8:19.3.0.0")
        implementation("org.xerial:sqlite-jdbc:3.42.0.1")
        implementation(libs.kotest.runner.junit5)

        implementation(kotlin("test-common"))
        implementation(kotlin("test-annotations-common"))

        // When running tests directly from intellij seems that this library needs to be referenced directly and not the bom-version
        // The other `platform` one causes an error: Caused by: java.lang.IllegalStateException: Missing embedded postgres binaries
        // (only when running directly from intellij using the GUI (i.e. Kotest plugin targets))
        implementation("io.zonky.test.postgres:embedded-postgres-binaries-linux-amd64:16.2.0")
        //implementation(project.dependencies.platform("io.zonky.test.postgres:embedded-postgres-binaries-bom:16.2.0"))

        implementation("org.flywaydb:flyway-core:7.15.0") // corresponding to embedded-postgres
        //implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
      }
    }

  }
}

tasks.named<Test>("jvmTest") {
  useJUnitPlatform()
  filter {
    isFailOnNoMatchingTests = false
  }
  testLogging {
    showExceptions = true
    showStandardStreams = true
    events = setOf(
      org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED,
      org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED
    )
    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
  }
}
