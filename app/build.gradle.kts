import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.baselineprofile)
    alias(libs.plugins.jetbrains.compose.compiler)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.jetbrains.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.wikirandomizerp.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.wikirandomizerp.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 54
        versionName = "1.0 beta"

        // Optional Firebase config for cross-device reading-history sync. Supplying these through
        // ~/.gradle/gradle.properties, CI secrets or -P flags keeps google-services.json optional.
        fun config(name: String): String =
            project.providers.gradleProperty(name)
                .orElse(project.providers.environmentVariable(name))
                .orElse("")
                .get()
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${config("FIREBASE_PROJECT_ID")}\"")
        buildConfigField("String", "FIREBASE_APPLICATION_ID", "\"${config("FIREBASE_APPLICATION_ID")}\"")
        buildConfigField("String", "FIREBASE_API_KEY", "\"${config("FIREBASE_API_KEY")}\"")
        buildConfigField("String", "FIREBASE_AUTH_DOMAIN", "\"${config("FIREBASE_AUTH_DOMAIN")}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        androidResources {
            generateLocaleConfig = true
        }
    }

    buildTypes {
        debug {
            // A debuggable app runs in ART's slow "debug" mode: no ahead-of-time compilation, no
            // baseline profiles, so Compose scrolling janks badly (the debug log showed exactly that).
            // This keeps the build type and debug signing but makes it run like a normal app.
            // Set to true only if you need to attach a debugger.
            isDebuggable = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Fast, installable test build: build_windows.bat release  (-PlocalRelease signs it with
            // the debug key). Compose runs several times faster than in the debug build.
            if (project.hasProperty("localRelease")) {
                signingConfig = signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
    lint {
        // New UI strings are English-only; don't let release lint fail on the other locales.
        disable += "MissingTranslation"
        // The debug build is no longer "debuggable", so Gradle treats it like a release target and
        // would run lint as a build gate (and fail on style findings). Lint is not needed to build.
        checkReleaseBuilds = false
        abortOnError = false
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        jniLibs {
            // libkiwix + libzim ship ~200 MB of .so files across four ABIs. Uncompressed native
            // libraries would make the APK almost that large, so use the legacy layout: the
            // libraries are compressed in the APK and extracted once at install time.
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

tasks.withType(Test::class) {
    testLogging {
        exceptionFormat = TestExceptionFormat.FULL
        events = setOf(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
        showStandardStreams = true
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.adaptive)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)

    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.coil3.coil.gif)
    implementation(libs.coil3.coil.svg)
    implementation(libs.coil3.compose)
    implementation(libs.coil3.network.okhttp)
    implementation(libs.ehsannarmani.compose.charts)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.material.kolor)
    implementation(libs.libkiwix)
    implementation(libs.okhttp)
    implementation(libs.retrofit2.converter.scalars)
    implementation(libs.retrofit2.kotlinx.serialization.converter)
    implementation(libs.retrofit2.retrofit)
    implementation(libs.latex2unicode.x.x3)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.billing.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    "baselineProfile"(project(":baselineprofile"))
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}