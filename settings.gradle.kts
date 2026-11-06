pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    // Kotlin/Wasm adds the Binaryen distribution repository from the plugin. Keep the default
    // PREFER_PROJECT mode so the wasm toolchain can resolve it; the project repositories below
    // still provide every Android/KMP dependency.
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "WikiRandomizer-P"
include(":app")
include(":baselineprofile")
// Platform-neutral code shared by the Android app and the SwiftUI iOS app.
include(":shared")
