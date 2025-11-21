plugins {
  alias(libs.plugins.kotlin.jvm)
  // No inclusion of `publish` here because this project is not published to maven directly
  id("maven-publish")
  id("conventions")
  id("publish-jvm")
  id("java-gradle-plugin")
  alias(libs.plugins.gradle.plugin.publish)
  alias(libs.plugins.buildconfig)
}

repositories {
  mavenCentral()
  mavenLocal()
}

version = extra["pluginProjectVersion"].toString()

kotlin {
  jvmToolchain(17)
}

val runtimeVersion = extra["pluginProjectVersion"].toString()
val decomatVersion = extra["decomatVersion"]
val serializationVersion = libs.versions.serialization.get()

val koogLibrary = project.dependencies.create(libs.koog.agents.get()).toString()
val coroutinesLibrary = project.dependencies.create(libs.kotlinx.coroutines.core.get()).toString()

buildConfig {
  buildConfigField("String", "VERSION", "\"${project.version}\"")
  buildConfigField("String", "DECOMAT_VERSION", "\"${decomatVersion}\"")
  buildConfigField("String", "SERIALIZATION_VERSION", "\"${serializationVersion}\"")
  buildConfigField("String", "KOOG_LIBRARY", "\"${koogLibrary}\"")
  buildConfigField("String", "COROUTINES_LIBRARY", "\"${coroutinesLibrary}\"")
}

dependencies {
  api("io.exoquery:exoquery-engine:${runtimeVersion}")
  implementation(kotlin("gradle-plugin-api"))
  implementation(kotlin("gradle-plugin"))
  compileOnly(gradleApi())
}

gradlePlugin {
  website = "https://github.com/exoquery/exoquery"
  vcsUrl = "https://github.com/exoquery/exoquery.git"
  plugins {
    create("exoqueryPlugin") {
      id = "io.exoquery.exoquery-plugin"
      displayName = "ExoQuery Plugin"
      description = "Kotlin exoquery Compiler Plugin"
      implementationClass = "io.exoquery.GradlePlugin"
      tags = listOf("kotlin", "exoquery", "jvm")
    }
  }
}