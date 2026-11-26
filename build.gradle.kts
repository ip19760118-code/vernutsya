plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.vernutsya"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.vernutsya"
        minSdk = 26
        targetSdk = 35
        versionCode = 12
        versionName = "2.10.0"
    }

    signingConfigs {
        create("release") {
            storeFile = file("vernutsya.jks")
            storePassword = "vernutsya123"
            keyAlias = "vernutsya"
            keyPassword = "vernutsya123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.osmdroid:osmdroid-android:6.1.18")

    // Без рекламных адаптеров и Google Play Services.
    // Рекламы и покупок в приложении нет, рекламный ID не нужен.
    implementation("io.appmetrica.analytics:analytics:8.6.0") {
        exclude(group = "io.appmetrica.analytics", module = "analytics-ad-revenue")
        exclude(group = "com.google.android.gms")
    }
}