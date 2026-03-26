rootProject.name = "sleepyio"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

// foojay-resolver-convention plugin removed: JDK is pre-installed,
// and the plugin artifact CDN (plugins-artifacts.gradle.org) is blocked in some environments

include(":composeApp")