plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.chaquopy)
}

// M3.3+: fixed dev signing config. The keystore is committed at
// `keystores/dev.keystore`. Every CI build signs with the same key, so
// users can install-over-upgrade without uninstalling first.
//
// M5 decision (v1.0.0-avbtool): ship with this keystore. The repo is a
// fork-friendly open-source tool intended for GitHub Release distribution,
// not Google Play; a shared signing key is an accepted trade-off for
// zero-config installability. If Play ever becomes a target, move to a
// CI-Secrets-backed release keystore.
val devKeystoreFile: File = rootProject.file("keystores/dev.keystore")
val devKeystoreStorePass: String = "avbtool-dev-store"
val devKeystoreAlias: String = "avbtool-dev"
val devKeystoreKeyPass: String = "avbtool-dev-store"

android {
    namespace = "com.bingyin.materialyouprefs"
    compileSdk = 35

    signingConfigs {
        create("devFixed") {
            storeFile = devKeystoreFile
            storePassword = devKeystoreStorePass
            keyAlias = devKeystoreAlias
            keyPassword = devKeystoreKeyPass
        }
    }

    defaultConfig {
        applicationId = "com.bingyin.materialyouprefs"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        debug {
            // Use the fixed dev keystore so every CI build has the same
            // signature (no need to uninstall before re-install).
            signingConfig = signingConfigs.getByName("devFixed")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // M5: release uses the dev keystore. See the top-of-file note
            // for the M5 sign-off on this trade-off.
            signingConfig = signingConfigs.getByName("devFixed")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs.useLegacyPackaging = false
        resources.excludes += "META-INF/INDEX.LIST"
        resources.excludes += "META-INF/io.netty.versions.properties"
        resources.excludes += "META-INF/AL2.0/LICENSE"
    }

    // M5 lint baseline: strip the four detectors that only fire on
    // version-upgrade suggestions and platform-scope preferences, neither
    // of which is a defect we should block the build on today.
    //
    //   - GradleDependency + AndroidGradlePluginVersion: warn on any
    //     pinned version that isn't the newest. Upgrading AGP 8.5 -> 9.4
    //     or Compose BOM 2024.09.02 -> 2026.08.00 would break the build
    //     and is out of scope for the v1.0.0-avbtool cut.
    //   - OldTargetApi: we deliberately pin targetSdk = 35 (Android 15)
    //     to match the compileSdk; bumping requires another regression
    //     pass.
    //   - ChromeOsAbiSupport: we don't ship x86_64; avbtool's Python
    //     runtime and FEC encoder are arm64-only.
    //
    // fatal = "Error" means lint failures block the build only on real
    // findings (Error severity); warnings are informational.
    lint {
        disable += listOf(
            "GradleDependency",
            "AndroidGradlePluginVersion",
            "OldTargetApi",
            "ChromeOsAbiSupport",
        )
        fatal += setOf("Error")
        abortOnError = true
    }
}

// M3.1: Chaquopy runtime.
//
// Reference: https://github.com/chaquo/chaquopy/blob/15.0.1/product/gradle-plugin/src/main/kotlin/PythonDsl.kt
//
// - `defaultConfig { version = ... }` selects Python version.
// - `pip { install(...) }` installs Python packages from https://chaquo.com/pypi-13.1/.
//   These are NOT Gradle dependencies; do not add them to `implementation(...)`.
// - `sourceSets.main` auto-creates `src/main/python/` -- no explicit srcDir needed.
// - `abiFilters` comes from `android.defaultConfig.ndk`, not from Chaquopy DSL.
// - Do NOT add `implementation("com.chaquo.python:python:...")` -- the Chaquopy
//   plugin wires the Python runtime + `com.chaquo.python.runtime` AAR
//   automatically. Adding it manually triggers 'Unresolved reference: python'
//   because no such dep alias exists in libs.versions.toml.
//
// M3.3: `pip { install("cryptography==43.0.1") }` was REVERTED because
// pypi-13.1 does not host an arm64-v8a wheel for `cryptography` at all
// (verified via curl to https://chaquo.com/pypi-13.1/simple/cryptography/ --
// HTTP 404). pip falls back to PyPI sdist, which uses `maturin` (Rust toolchain)
// to build, and the GitHub runner doesn't have maturin installed. See
// `dev-log/DEVLOG.md` M3.3 entry for full trace. Next M3.3 iteration will
// switch avbtool.py to pure-Python RSA (pow(a,d,n)) so no external dep needed.
chaquopy {
    defaultConfig {
        version = "3.12"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    platform(libs.androidx.compose.bom)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    debugImplementation(libs.androidx.ui.tooling)

    // M1: Room (M2 data layer)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // M1: coroutines + JSON + DI annotations
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.javax.inject)

    // M3: Chaquopy plugin auto-injects the Python runtime; no `implementation` line
    // needed here. Python libraries, when added later, go via `pip { install(...) }`
    // in the chaquopy block above.

    // Tests
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
