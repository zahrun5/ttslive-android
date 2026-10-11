import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.zahxrun.ttslive"
    // RootEncoder 2.6.x menarik CameraX 1.5 yang mewajibkan compileSdk 36.
    compileSdk = 36

    defaultConfig {
        applicationId = "com.zahxrun.ttslive"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Kotlin 2.2: blok kotlinOptions {} sudah jadi error, diganti compilerOptions.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.savedstate:viewmodel-savedstate:1.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    // Encoder + RTMP untuk streaming kanvas game ke YouTube/TikTok.
    // 2.6.7 dibuild dengan Kotlin 2.2.21 + AGP 8.13.2, jadi cocok dengan toolchain di atas.
    // (2.8.1 dibuild dengan Kotlin 2.4 dan tidak bisa dibaca Kotlin 2.0/2.2.)
    implementation("com.github.pedroSG94.RootEncoder:library:2.6.7")
}
