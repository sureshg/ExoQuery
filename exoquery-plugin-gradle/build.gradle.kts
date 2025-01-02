plugins {
    kotlin("jvm") version "2.1.0"

    // No inclusion of `publish` here because this project is not published to maven directly
    id("maven-publish")
    id("conventions")
    id("publish-jvm")
    id("java-gradle-plugin")
    id("com.gradle.plugin-publish") version "1.1.0"
    id("com.github.gmazzo.buildconfig") version "3.1.0"
}

kotlin {
    jvmToolchain(11)
}

val conventionsDecomatVersion = Conventions_gradle.Versions.decomatVersion

buildConfig {
    buildConfigField("String", "VERSION", "\"${project.version}\"")
    buildConfigField("String", "DECOMAT_VERSION", "\"${conventionsDecomatVersion}\"")
}

dependencies {
    implementation(kotlin("gradle-plugin-api"))
}

gradlePlugin {
    website.set("https://github.com/exoquery/exoquery")
    vcsUrl.set("https://github.com/exoquery/exoquery.git")

    plugins {
        create("exoqueryPlugin") {
            id = "io.exoquery.exoquery-plugin"
            displayName = "ExoQuery Plugin"
            description = "Kotlin exoquery Compiler Plugin"
            implementationClass = "io.exoquery.GradlePlugin"

            tags.set(listOf("kotlin", "exoquery", "jvm"))
        }
    }
}


//plugins {
//    id("conventions")
//
//    id("java-gradle-plugin")
//    id("com.gradle.plugin-publish") version "1.1.0"
//
//    id("com.github.gmazzo.buildconfig") version "3.1.0"
//}
//
//kotlin {
//    jvmToolchain(11)
//}
//
//buildConfig {
//    buildConfigField("String", "VERSION", "\"${project.version}\"")
//}
//
//dependencies {
//    implementation(kotlin("gradle-plugin-api"))
//}
//
//gradlePlugin {
//    website.set("https://github.com/TBD")
//    vcsUrl.set("https://github.com/TBD.git")
//
//    plugins {
//        create("exoqueryPlugin") {
//            id = "io.exoquery.exoquery-plugin"
//            displayName = "ExoQuery Plugin"
//            description = "Kotlin Compiler Plugin for ExoQuery"
//            implementationClass = "io.exoquery.GradlePlugin"
//
//            tags.set(listOf("kotlin", "exoquery", "jvm"))
//        }
//    }
//}
