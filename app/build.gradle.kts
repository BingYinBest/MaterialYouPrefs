plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    // M3: alias(libs.plugins.chaquopy)
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
        // Restrict to arm64-v8a only (ADR-004). Note: `ndk { abiFilters }` is a
        // child of `defaultConfig`, not of `android`. Moving it out was a CI compile
        // failure (Unresolved reference: ndk at build.gradle.kts:49).
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

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

// M3 才真正需要 Chaquopy（要拷贝 avbtool.py + 依赖）；M1/M2 阶段先禁用 chaquopy 块，
// 避免空 sourceDirs 触发 plugin 报错。toml 里的 plugin/dep 定义保留，M3 只需解开注释。
//
// chaquopy {
//     defaultVersion("3.12")
//     version("3.12")
//     abiFilters("arm64-v8a")
//     sourceDirs = setOf("src/main/python")
// }

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
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    debugImplementation(libs.androidx.ui.tooling)

    // M1: Room (M2 data layer)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // M1: coroutines (CommandRepository Flow APIs)
    implementation(libs.kotlinx.coroutines.android)

    // M1: JSON serialization (seed JSON + paramsJson)
    implementation(libs.kotlinx.serialization.json)

    // M1: @Inject / @Singleton annotations (no DI framework yet, see ADR-010)
    implementation(libs.javax.inject)

    // M3: Chaquopy + cryptography (commented out to avoid empty sourceDirs error)
    // implementation(libs.chaquopy.python)
    // implementation(libs.chaquopy.cryptography)
    // implementation(libs.chaquopy.pyyaml)

    // Tests (added when DAO tests land in M2.5)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
