pluginManagement {
    val quarkusPluginId: String by settings
    val quarkusPluginVersion: String by settings
    repositories {
        mavenCentral()
        gradlePluginPortal()
        mavenLocal()
    }
    plugins {
        id(quarkusPluginId) version quarkusPluginVersion
    }
}

rootProject.name = "diama"

include(
    "diama-common",
    "diama-domain",
    "diama-infra",
    "diama-api",
)
