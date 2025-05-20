pluginManagement {
  includeBuild("../build-logic")
  repositories {
    gradlePluginPortal()
    mavenCentral()
  }
}

dependencyResolutionManagement {
  // So the libs.versions.toml file can be used in the exoquery-engine, exoquery-plugin-kotlin, and exoquery-plugin-gradle builds
  versionCatalogs {
    create("libs") {
      from(files(rootDir.parentFile.resolve("gradle/libs.versions.toml")))
    }
  }
}
