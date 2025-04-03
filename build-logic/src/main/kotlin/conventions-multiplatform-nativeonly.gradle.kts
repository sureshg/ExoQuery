import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.konan.target.HostManager

plugins {
  id("conventions")
  kotlin("multiplatform")
}


repositories {
  //mavenLocal() // Don't include this, it causes all sorts of build horror
  mavenCentral()
  mavenLocal()
}


/*
This thing is necessary because we can't use conventions-multiplatform with exoquery-native because conventions-multiplatform
enables JVM by default (I need this for testing an exoquery-runtime functionality) which we can't do for exoquery-native as it
has no JVM-target native libraries i.e. (Terpal's) controller-native doesn't work for JVM (that's what controller-jvm is for)
and the sqlite libraries that it depends on don't have JVM targets either.
 */
kotlin {

  val isCI = project.hasProperty("isCI")
  // I.e. set this environment variable specifically to true to build (most) targets
  val fullLocal = !isCI && ((System.getenv("EXOQUERY_FULL_LOCAL")?.toBoolean() ?: false) || project.hasProperty("isLocalMultiplatform"))

  when {
    HostManager.hostIsLinux -> {
      linuxX64()
      if (isCI || fullLocal) {
        iosX64()
        iosArm64()
        iosSimulatorArm64()
        linuxArm64()

        // Need to know about this since we publish the -tooling metadata from
        // the linux containers. Although it doesn't build these it needs to know about them.
        macosX64()
        iosX64()
        iosArm64()
        watchosArm32()
        watchosArm64()
        watchosX64()
        tvosArm64()
        tvosX64()
        macosArm64()
        iosSimulatorArm64()
        mingwX64()
        // Terpal-Runtime not published for these yet
        //watchosDeviceArm64()
        //tvosSimulatorArm64()
        //watchosSimulatorArm64()
      }
    }
    HostManager.hostIsMingw -> {
      mingwX64()
    }
    HostManager.hostIsMac -> {
      macosX64()
      if (isCI || fullLocal) {
        iosX64()
        iosArm64()
        watchosArm32()
        watchosArm64()
        watchosX64()
        tvosArm64()
        tvosX64()
        macosArm64()
        iosSimulatorArm64()
        // Terpal-Runtime not published for these yet
        //watchosDeviceArm64()
        //tvosSimulatorArm64()
        //watchosSimulatorArm64()
      }
    }
  }
}

tasks.withType<AbstractTestTask>().configureEach {
    testLogging {
        showStandardStreams = true
        showExceptions = true
        exceptionFormat = TestExceptionFormat.SHORT
        events(TestLogEvent.STARTED, TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
    }
}
