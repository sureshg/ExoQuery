plugins {
    kotlin("jvm") version "1.8.21" apply false
}

subprojects {
    repositories {
        mavenCentral()
        maven("https://oss.sonatype.org/content/repositories/releases")
        mavenLocal()
    }
}