plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.it_inventoryeq"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.it_inventoryeq"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = false
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.recyclerview)
    implementation(libs.swiperefreshlayout)
    implementation(libs.cardview)

    // Barcode & QR Code Scanning (ZXing Embedded + Core)
    implementation(libs.zxing.embedded)
    implementation(libs.zxing.core)

    // Network & JSON Parsing for Backend Sync
    implementation(libs.okhttp)
    implementation(libs.gson)

    // Firebase Firestore & Auth SDK
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.common)

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
