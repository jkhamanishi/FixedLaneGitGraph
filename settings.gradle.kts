pluginManagement {
    repositories {
        maven { url = uri("./.gradle/.m2") }
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "Fixed Lane Git Graph"
