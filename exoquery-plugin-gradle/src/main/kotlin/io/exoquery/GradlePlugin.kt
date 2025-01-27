package io.exoquery

import io.exoquery.exoquery_plugin_gradle.BuildConfig
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetContainer
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import java.io.File

class GradlePlugin : KotlinCompilerPluginSupportPlugin {
    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

    override fun getCompilerPluginId(): String = "io.exoquery.exoquery-plugin"

    override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
        groupId = "io.exoquery",
        artifactId = "exoquery-plugin-kotlin",
        version = BuildConfig.VERSION
    )

    override fun apply(target: Project) {
        // This adds dependency until it has been configured by kotlin plugin
        // with the below code (starting with isMultiplatform) I don't think this is needed
        //target.plugins.withId("org.jetbrains.kotlin.jvm") {
        //    target.dependencies.add("implementation", "io.exoquery:exoquery-runtime:${BuildConfig.VERSION}")
        //}

        val isMultiplatform = target.plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")
        val sourceSetName = if (isMultiplatform) "commonMain" else "main"
        val sourceSetApiConfigName =
            target.extensions.getByType(KotlinSourceSetContainer::class.java).sourceSets.getByName(sourceSetName).apiConfigurationName
        val runtimeDependencies = buildList {
            add(target.dependencies.create("io.exoquery:exoquery-runtime:${BuildConfig.VERSION}"))
        }
        target.configurations.getByName(sourceSetApiConfigName).dependencies.addAll(runtimeDependencies)


        // Needed for the plugin classpath
        // Note that these do not bring in transitive dependencies so every transitive needs to be explicitly specified!
        target.plugins.withId("org.jetbrains.kotlin.multiplatform") {
            target.dependencies.add("kotlinNativeCompilerPluginClasspath", "io.exoquery:terpal-runtime:2.1.0-2.0.0.PL")
            target.dependencies.add("kotlinNativeCompilerPluginClasspath", "io.exoquery:pprint-kotlin-core-jvm:3.0.0")
            target.dependencies.add("kotlinNativeCompilerPluginClasspath", "io.exoquery:pprint-kotlin-kmp-jvm:3.0.0")
            // Fansi and core pprint ADT come from here
            target.dependencies.add("kotlinNativeCompilerPluginClasspath", "io.exoquery:pprint-kotlin:3.0.0")
            // in some places the compiler needs to print things, so the compiled plugin needs pprint
            target.dependencies.add("kotlinNativeCompilerPluginClasspath", "io.exoquery:exoquery-runtime:${BuildConfig.VERSION}")
            target.dependencies.add("kotlinNativeCompilerPluginClasspath", "org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.3")
            target.dependencies.add("kotlinNativeCompilerPluginClasspath", "org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.7.3")
            // Since this is compiler-plugin it works in the compiler which is written in Java so we use JVM dependencies
            target.dependencies.add("kotlinNativeCompilerPluginClasspath", "io.exoquery:decomat-core-jvm:${BuildConfig.DECOMAT_VERSION}")
        }
    }

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project

        // ALSO needed for the plugin classpath
        kotlinCompilation.dependencies {
            api("io.exoquery:exoquery-runtime:${BuildConfig.VERSION}")
        }

        val target = kotlinCompilation.target.name
        val sourceSetName = kotlinCompilation.defaultSourceSet.name

        return project.provider {
            listOf(
                SubpluginOption("projectDir", project.projectDir.path),
                SubpluginOption("projectBaseDir", project.project.projectDir.canonicalPath),
                SubpluginOption("kotlinOutputDir", getKspKotlinOutputDir(project, sourceSetName, target).path),
                SubpluginOption("resourceOutputDir", getKspResourceOutputDir(project, sourceSetName, target).path)
            )
        }
    }
    companion object {
        @JvmStatic
        fun getKspKotlinOutputDir(project: Project, sourceSetName: String, target: String) =
            File(getExoOutputDir(project, sourceSetName, target), "kotlin")

        @JvmStatic
        fun getKspResourceOutputDir(project: Project, sourceSetName: String, target: String) =
            File(getExoOutputDir(project, sourceSetName, target), "resources")

        @JvmStatic
        fun getExoOutputDir(project: Project, sourceSetName: String, target: String) =
            project.layout.buildDirectory.file("generated/exo/$target/$sourceSetName").get().asFile
    }
}
