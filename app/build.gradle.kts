plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.chaquopy)
}

android {
    namespace = "com.bingyin.materialyouprefs"
    compileSdk = 35

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
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
}

// M3.1: Chaquopy runtime.
// DSL reference: https://github.com/chaquo/chaquopy/blob/15.0.1/product/gradle-plugin/src/main/kotlin/PythonDsl.kt
// - `defaultConfig { }` holds version + pip + staticProxy + extractPackages
// - `sourceSets { main { srcDir(...) } }` controls Python source directories.
//   The plugin auto-creates `src/main/python/` so no explicit srcDir is needed
//   for our M3.1 layout.
// - `abiFilters` is NOT part of Chaquopy DSL; it comes from `android.defaultConfig.ndk`.
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

    // M3: Chaquopy runtime. Python libraries (cryptography, pycryptodome,
    // etc.) are installed via `chaquopy.defaultConfig.pip.install(...)` in
    // M3.2+ once we start vendoring avbtool.py.
    implementation(libs.chaquopy.python)

    // Tests
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
