plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "mx.clubsanfrancisco.golfgps.wear"
    compileSdk = 34

    defaultConfig {
        applicationId = "mx.clubsanfrancisco.golfgps"
        minSdk = 30
        targetSdk = 34
        // Play Store exige versionCode distinto al del módulo app (mismo paquete).
        versionCode = 2
        versionName = "1.0"
    }
    // Misma llave fija que el módulo app (updates sin desinstalar).
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("signing/sfgolf-debug.keystore")
            storePassword = "android"
            keyAlias = "sfgolf"
            keyPassword = "android"
        }
        // Llave de SUBIDA a Play Store, compartida con el módulo app.
        // NO está en el repo: el CI la reconstruye desde los secretos.
        create("release") {
            val pwFile = rootProject.file("signing/release-password.txt")
            if (pwFile.exists()) {
                storeFile = rootProject.file("signing/sfgolf-release.keystore")
                storePassword = pwFile.readText().trim()
                keyAlias = "sfgolf"
                keyPassword = pwFile.readText().trim()
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (rootProject.file("signing/release-password.txt").exists())
                signingConfigs.getByName("release") else null
        }
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
