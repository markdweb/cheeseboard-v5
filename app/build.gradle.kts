
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.colorgrid.keyboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.colorgrid.keyboard"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
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
        compose = true
    }
}

dependencies {
    // Android Core
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))

    // Compose UI
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")

    // Compose Runtime - FIX
    implementation("androidx.compose.runtime:runtime-saveable")

    // Material Design
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
}
