import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.konan.target.HostManager

plugins {
  id("conventions")
  kotlin("multiplatform")
}


kotlin {
  val isCI = project.hasProperty("isCI")
  val fullLocal = !isCI && ((System.getenv("EXOQUERY_FULL_LOCAL")?.toBoolean() ?: false) || project.hasProperty("isLocalMultiplatform"))
  jvmToolchain(17)

  jvm {
  }
  linuxX64()
  macosX64()

  val linuxCI = HostManager.hostIsLinux && isCI
  val mingCI = HostManager.hostIsMingw && isCI
  val macCI = HostManager.hostIsMac && isCI

  if (linuxCI)
    js {
      browser()
      nodejs()
    }
  if (linuxCI) linuxArm64()
  // LinuxCI Needs to know the OSX and MingW dependencies exist even though it does not build them so it can put them in the mmodules-list metadata in maven-central.
  if (linuxCI || macCI) macosArm64()
  if (linuxCI || macCI) iosX64()
  if (linuxCI || macCI) iosArm64()
  if (linuxCI || macCI) tvosX64()
  if (linuxCI || macCI) tvosArm64()
  if (linuxCI || macCI) watchosX64()
  if (linuxCI || macCI) watchosArm32()
  if (linuxCI || macCI) watchosArm64()
  if (linuxCI || macCI) iosSimulatorArm64()
  //if (linux || mac) watchosSimulatorArm64()
  //if (linux || mac) watchosDeviceArm64()
  //if (linux || mac) tvosSimulatorArm64()
  //if (linux || mac) watchosSimulatorArm64()
  if (linuxCI || mingCI) mingwX64()

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
