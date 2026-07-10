plugins {
    // AGP 8.7 es la mínima que compila con compileSdk 35 (Android 15), el
    // target que Google Play exige para apps nuevas en 2026.
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("com.google.gms.google-services") version "4.4.1" apply false
}
