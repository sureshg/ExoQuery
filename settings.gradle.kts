pluginManagement {
    includeBuild("exoquery-plugin-gradle")
    includeBuild("build-logic")
}

includeBuild("exoquery-runtime")
includeBuild("exoquery-plugin-kotlin")

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
