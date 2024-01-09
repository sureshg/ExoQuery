plugins {
    id("conventions")

    `java-library`
    `maven-publish`
    signing
}

java {
    withJavadocJar()
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("exoquery") {
            from(components["java"])

            artifactId = project.name

            versionMapping {
                usage("java-api") {
                    fromResolutionOf("runtimeClasspath")
                }
                usage("java-runtime") {
                    fromResolutionResult()
                }
            }

            pom {
                name.set("ExoQuery")
                description.set("Source code capture plugin")
                url.set("https://exoquery.io")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }

                developers {
                    developer {
                        name.set("Alexander Ioffe")
                        url.set("https://github.com/TBD")
                    }
                }

                scm {
                    connection.set("scm:git@github.com:TBD.git")
                    developerConnection.set("scm:git@github.com:TBD.git")
                    url.set("TBD")
                }
            }
        }
    }

    signing {
        useInMemoryPgpKeys(
            System.getenv("GPG_PRIVATE_KEY"),
            System.getenv("GPG_PRIVATE_PASSWORD")
        )

        sign(publishing.publications["exoquery"])
    }

    repositories {
        mavenLocal()
        maven {
            val repoId = System.getenv("REPOSITORY_ID")

            name = "OSSRH"
            url = uri("https://s01.oss.sonatype.org/service/local/staging/deployByRepositoryId/$repoId/")

            credentials {
                username = System.getenv("SONATYPE_USERNAME")
                password = System.getenv("SONATYPE_PASSWORD")
            }
        }
    }
}