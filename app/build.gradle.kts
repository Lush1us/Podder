import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application") version "9.0.0"
    id("org.jetbrains.kotlin.android") version "2.1.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0"
    id("com.google.devtools.ksp") version "2.1.0-1.0.29"
}

android {
    namespace = "com.example.podder"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.podder"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        multiDexEnabled = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
    }
    // composeOptions and kotlinCompilerExtensionVersion are removed in AGP 9.0!
}

dependencies {
    // Core Library Desugaring for java.time APIs on API < 26
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation(platform("androidx.compose:compose-bom:2025.10.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.6.1")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Networking & XML Parsing (RSS)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    implementation("io.github.pdvrieze.xmlutil:serialization:0.86.3")
    implementation("com.squareup.retrofit2:converter-scalars:2.11.0")

    // Image Loading
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Audio (Media3)
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")
    implementation("androidx.media3:media3-common:1.5.1")
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.8.0")

    // Room
    implementation("androidx.room:room-runtime:2.7.0-alpha01")
    implementation("androidx.room:room-ktx:2.7.0-alpha01")
    ksp("androidx.room:room-compiler:2.7.0-alpha01")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
}
