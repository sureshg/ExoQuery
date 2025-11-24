import org.jetbrains.kotlin.gradle.targets.js.webpack.*

// Includes everything from multiplatform-native + jvm builds

plugins {
  id("conventions")
  id("conventions-multiplatform-nativeonly")
  kotlin("multiplatform")
}

tasks.withType<KotlinWebpack>().configureEach {
  sourceMaps = true
  mode = KotlinWebpackConfig.Mode.DEVELOPMENT
}
