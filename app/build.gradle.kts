plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.jarvis.assistant"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.jarvis.assistant"
        minSdk = 26          // Android 8.0+ (covers Android 13 / API 33 devices)
        targetSdk = 33       // Android 13
        versionCode = 1
        versionName = "1.0"
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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-service:2.8.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Networking (Claude API calls)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20240303")

    // Wake word detection ("Hey Jarvis")
    implementation("ai.picovoice:porcupine-android:3.0.2")

    // On-device offline LLM (Gemma via MediaPipe)
    implementation("com.google.mediapipe:tasks-genai:0.10.14")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
