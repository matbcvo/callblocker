plugins {
    id("com.android.application")
}

// Release signing is driven entirely by environment variables so the keystore never
// enters the repo. Absent them (any local build), the release build stays unsigned.
val keystorePath: String? = System.getenv("KEYSTORE_FILE")

// CI sets this from the git tag; local builds fall back.
val appVersionName: String = System.getenv("VERSION_NAME") ?: "1.0"

// Names the build outputs after the app and its version rather than the Gradle default
// of "app", so a downloaded artifact says which release it is.
base {
    archivesName.set("callblocker-$appVersionName")
}

android {
    namespace = "com.matbcvo.callblocker"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.matbcvo.callblocker"
        // CallScreeningService can only reject calls from API 29 (Android 10) onwards,
        // where an app can hold ROLE_CALL_SCREENING without being the default dialer.
        minSdk = 29
        // Play requires new apps to target API 36 from 31 Aug 2026.
        targetSdk = 36
        // Play rejects a re-upload of an already-used versionCode, so CI overrides it
        // with a monotonic build number.
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = appVersionName
    }

    signingConfigs {
        create("release") {
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = if (keystorePath != null) {
                signingConfigs.getByName("release")
            } else {
                null
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // AGP 9 removed the `kotlinOptions` block; Kotlin's JVM target is configured
    // through the built-in Kotlin DSL below.

    buildFeatures {
        buildConfig = false
    }

    packaging {
        resources.excludes += setOf("META-INF/*.version", "kotlin/**", "DebugProbesKt.bin")
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

// No dependencies on purpose: the whole app runs on framework APIs, which keeps the
// release artifact tiny once R8 shrinks the Kotlin stdlib.
dependencies {}
