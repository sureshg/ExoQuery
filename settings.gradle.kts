pluginManagement {
    includeBuild("exoquery-plugin-gradle")
    includeBuild("build-logic")

    repositories {
        gradlePluginPortal()
        mavenCentral()
        mavenLocal()
        google()
    }
}

// Need this or you will get:
// Using a toolchain installed via auto-provisioning, but having no toolchain repositories configured. This behavior is deprecated. Consider defining toolchain download repositories, otherwise the build might fail in clean environments; see https://docs.gradle.org/8.6/userguide/toolchains.html#sub:download_repositories
// at org.gradle.jvm.toolchain.internal.JavaToolchainQueryService.warnIfAutoProvisionedToolchainUsedWithoutRepositoryDefinitions(JavaToolchainQueryService.java:171)
// at org.gradle.jvm.toolchain.internal.JavaToolchainQueryService.lambda$findInstalledToolchain$6(JavaToolchainQueryService.java:157)
// at java.base/java.util.Optional.map(Optional.java:260)
// at org.gradle.jvm.toolchain.internal.JavaToolchainQueryService.findInstalledToolchain(JavaToolchainQueryService.java:156)
dependencyResolutionManagement {
    repositories {
        mavenCentral() // Required for toolchain resolution
    }
}

includeBuild("exoquery-runtime")
includeBuild("exoquery-plugin-kotlin")

include("exoquery-controller-common")

val isCI: Boolean by settings.extra { false }
val isLocal = !isCI
val isLinux = System.getProperty("os.name").toLowerCase().contains("linux")

// If it's a local build or we're building the CI onl linux include the android project
if (isLocal || isLinux) {
    val buildLabel =
        if (isLocal) "Local"
        else if (isLinux) "Linux"
        else "Unknown"

    println("------------------- Building JDBC/Android Libraries for ${buildLabel} Build -------------------")
    include("exoquery-jdbc")
    include("exoquery-android")
}

include("exoquery-native")
include("testing")

rootProject.name = "exoquery"



//pluginManagement {
//    includeBuild("exoquery-plugin-gradle")
//    repositories {
//        gradlePluginPortal()
//        mavenCentral()
//    }
//}
//
//includeBuild("exoquery-runtime")
//includeBuild("exoquery-plugin-kotlin")
//
//include("testing")
////include("readme")
