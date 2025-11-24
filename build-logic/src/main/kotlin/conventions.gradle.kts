import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

internal val Project.libs
    get() = the<LibrariesForLibs>()

// For exoquery-engine, exoquery-plugin-gradle, exoquery-plugin-kotlin
extra["pluginProjectVersion"] = "${libs.versions.kotlin.get()}-${libs.versions.pluginVersion.get()}"
// For exoquery-runner-core, exoquery-runner-jdbc, exoquery-runner-android, exoquery-runner-native
extra["controllerProjectVersion"] = libs.versions.pluginVersion.get()
extra["decomatVersion"] = libs.versions.decomat.get()

group = "io.exoquery"
// Default version is the plugin-project version. Overridden in the subprojects
version = extra["pluginProjectVersion"].toString()

check("$version".isNotBlank() && version != "unspecified")
{ "invalid version $version" }

tasks.withType<AbstractTestTask> {
  testLogging {
    lifecycle {
      events = mutableSetOf(TestLogEvent.FAILED, TestLogEvent.PASSED, TestLogEvent.SKIPPED)
      exceptionFormat = TestExceptionFormat.FULL

      showExceptions = true
      showCauses = true
      showStackTraces = false
      showStandardStreams = false
    }
    info.events = lifecycle.events
    info.exceptionFormat = lifecycle.exceptionFormat
  }

  val failedTests = mutableListOf<TestDescriptor>()
  val skippedTests = mutableListOf<TestDescriptor>()

  addTestListener(object : TestListener {
    override fun beforeSuite(suite: TestDescriptor) {}

    override fun beforeTest(testDescriptor: TestDescriptor) {}

    override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {
      when (result.resultType) {
        TestResult.ResultType.FAILURE -> failedTests.add(testDescriptor)
        TestResult.ResultType.SKIPPED -> skippedTests.add(testDescriptor)
        else -> Unit
      }
    }

    override fun afterSuite(suite: TestDescriptor, result: TestResult) {
      if (suite.parent == null) {
        logger.lifecycle("################ Summary::Start ################")
        logger.lifecycle("Test result: ${result.resultType}")
        logger.lifecycle(
          "Test summary: ${result.testCount} tests, " +
              "${result.successfulTestCount} succeeded, " +
              "${result.failedTestCount} failed, " +
              "${result.skippedTestCount} skipped"
        )
        failedTests.takeIf { it.isNotEmpty() }?.prefixedSummary("\tFailed Tests")
        skippedTests.takeIf { it.isNotEmpty() }?.prefixedSummary("\tSkipped Tests:")
        logger.lifecycle("################ Summary::End ##################")
      }
    }

    private infix fun List<TestDescriptor>.prefixedSummary(subject: String) {
      logger.lifecycle(subject)
      forEach { test -> logger.lifecycle("\t\t${test.displayName()}") }
    }

    private fun TestDescriptor.displayName() = parent?.let { "${it.name} - $name" } ?: name

  })
}
