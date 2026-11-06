import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * Platform-neutral core of WikiRandomizer-P.
 *
 * The Android app consumes the `androidTarget` variant directly. The SwiftUI app in `iosApp`
 * links the static `shared` framework produced by the three iOS targets, so URL construction,
 * title handling and the small text helpers behave identically on both platforms. The browser
 * PWA imports the same code as a Kotlin/Wasm library.
 */
plugins {
    alias(libs.plugins.jetbrains.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

kotlin {
    // A JVM target keeps the shared tests runnable on CI/Linux without an Android emulator.
    jvm()

    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // The PWA loads this as a small WASM library; the DOM/UI stays in plain JavaScript so the
    // app shell is cacheable and works on older browsers with a JavaScript fallback.
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.library()
        generateTypeScriptDefinitions()
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "shared"
            isStatic = true
        }
    }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "org.nsh07.wikireader.shared"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
