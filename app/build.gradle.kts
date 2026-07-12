plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "mx.clubsanfrancisco.golfgps"
    compileSdk = 34

    defaultConfig {
        applicationId = "mx.clubsanfrancisco.golfgps"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // Llave de firma FIJA (versionada en el repo): permite actualizar la app
    // sin desinstalar, aunque el APK venga de builds distintos del CI.
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("signing/sfgolf-debug.keystore")
            storePassword = "android"
            keyAlias = "sfgolf"
            keyPassword = "android"
        }
        // Llave de SUBIDA a Play Store. NO está en el repo (es público): el CI la
        // reconstruye desde los secretos SIGNING_KEYSTORE_BASE64 y SIGNING_KEY_PASSWORD.
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
            // Sin la llave (fork o build local sin secretos) el AAB sale sin firmar.
            signingConfig = if (rootProject.file("signing/release-password.txt").exists())
                signingConfigs.getByName("release") else null
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
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("com.google.android.gms:play-services-wearable:18.1.0")
    // Cuenta opcional + respaldo en la nube (se activa al agregar google-services.json)
    implementation(platform("com.google.firebase:firebase-bom:32.7.4"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
}

// El plugin de Google Services solo se aplica si existe la configuración del
// proyecto Firebase. Sin el json, la app compila y corre 100% local.
if (project.file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}
