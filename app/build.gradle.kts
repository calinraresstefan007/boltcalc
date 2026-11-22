plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "ro.marius.boltcalc"
    compileSdk = 34
    defaultConfig {
        applicationId = "ro.marius.boltcalc"
        minSdk = 26
        targetSdk = 34
        versionCode = 83
        versionName = "83.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    signingConfigs {
        create("release") {
            storeFile = rootProject.file("release.keystore")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "android"
            keyAlias = "release"
            keyPassword = System.getenv("KEY_PASSWORD") ?: "android"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isDebuggable = true
        }
    }
}
