plugins {
    id("conventions")

    id("java-gradle-plugin")
    id("com.gradle.plugin-publish") version "1.1.0"

    id("com.github.gmazzo.buildconfig") version "3.1.0"
}

buildConfig {
    buildConfigField("String", "VERSION", "\"${project.version}\"")
}

dependencies {
    implementation(kotlin("gradle-plugin-api"))
}

gradlePlugin {
    website.set("https://github.com/TBD")
    vcsUrl.set("https://github.com/TBD.git")

    plugins {
        create("exoqueryPlugin") {
            id = "io.exoquery.exoquery-plugin"
            displayName = "ExoQuery Plugin"
            description = "Kotlin Compiler Plugin for ExoQuery"
            implementationClass = "io.exoquery.GradlePlugin"

            tags.set(listOf("kotlin", "exoquery", "jvm"))
        }
    }
}
