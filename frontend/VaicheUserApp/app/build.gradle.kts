plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-parcelize")
    id("androidx.navigation.safeargs.kotlin")
}

android {
    namespace = "com.example.vaicheuserapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.vaicheuserapp"
        minSdk = 24
        //noinspection OldTargetApi
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
    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.lifecycle.common.java8)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(libs.material)
    implementation(libs.retrofit)
    implementation (libs.converter.gson)

    // OkHttp & Logging Interceptor (for debugging network calls)
    implementation (libs.okhttp)
    implementation (libs.logging.interceptor)

    // Coroutines for asynchronous calls
    implementation (libs.kotlinx.coroutines.android)
    implementation (libs.androidx.lifecycle.runtime.ktx) // For lifecycleScope

    implementation(libs.coil)

    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    implementation(libs.androidx.navigation.fragment.ktx.v293)
    implementation(libs.androidx.navigation.ui.ktx.v293)
}