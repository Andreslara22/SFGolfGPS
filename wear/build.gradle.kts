plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "mx.clubsanfrancisco.golfgps.wear"
    compileSdk = 35

    defaultConfig {
        applicationId = "mx.clubsanfrancisco.golfgps"
        minSdk = 30
        targetSdk = 34
        versionCode = 3
        versionName = "1.2"
    }
    // Misma llave fija que el módulo app (updates sin desinstalar).
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("signing/sfgolf-debug.keystore")
            storePassword = "android"
            keyAlias = "sfgolf"
            keyPassword = "android"
        }
        // Misma llave de release que el módulo app (ver app/build.gradle.kts).
        create("release") {
            storeFile = rootProject.file("signing/sfgolf-upload.jks")
            storePassword = "sanfrancisco2026"
            keyAlias = "sfgolf-upload"
            keyPassword = "sanfrancisco2026"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.8" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.wear.compose:compose-material:1.3.0")
    implementation("androidx.wear.compose:compose-foundation:1.3.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("com.google.android.gms:play-services-wearable:18.1.0")
    implementation("androidx.wear:wear:1.3.0")
    implementation("androidx.wear:wear-ongoing:1.0.0")
    implementation("androidx.wear.tiles:tiles:1.2.0")
    implementation("androidx.wear.watchface:watchface-complications-data-source:1.2.1")
    implementation("androidx.concurrent:concurrent-futures:1.1.0")
}
