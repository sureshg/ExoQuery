import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.konan.target.HostManager

plugins {
  id("conventions")
  kotlin("multiplatform")
}


kotlin {
  val isCI = project.hasProperty("isCI")
  val fullLocal = !isCI && ((System.getenv("EXOQUERY_FULL_LOCAL")?.toBoolean() ?: false) || project.hasProperty("isLocalMultiplatform"))
  jvmToolchain(11)

  jvm {
  }

  when {
    HostManager.hostIsLinux -> {
      linuxX64()
      if (isCI || fullLocal) {
        linuxArm64()

        js {
          browser()
          nodejs()
        }

        // Kotlinx-datetime doesn't exist for this
        // @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
        // wasmWasi()
        // @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
        // wasmJs()

        androidNativeX64()
        androidNativeX86()
        androidNativeArm32()
        androidNativeArm64()
      }
    }
    HostManager.hostIsMingw -> {
      mingwX64()
    }
    HostManager.hostIsMac -> {
      macosX64()
      if (isCI || fullLocal) {
        macosArm64()
        iosX64()
        iosArm64()
        iosSimulatorArm64()
        tvosX64()
        tvosArm64()
        watchosX64()
        watchosArm32()
        watchosArm64()


        //tvosSimulatorArm64()
        //watchosDeviceArm64()
        //watchosSimulatorArm64()
      }
    }
  }

    sourceSets {
        commonMain {
            kotlin.srcDir("$buildDir/templates/")
            dependencies {
            }
        }

        commonTest {
            kotlin.srcDir("$buildDir/templates/")
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
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
