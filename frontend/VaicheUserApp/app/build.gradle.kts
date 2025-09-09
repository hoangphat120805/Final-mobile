plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-parcelize")
    id("androidx.navigation.safeargs.kotlin")
}

android {
    packagingOptions {
        resources {
            excludes += setOf(
                // To compile the current version of UX Framework you need to add only these two lines:
                "META-INF/DEPENDENCIES",
                "META-INF/INDEX.LIST",
            )
        }
    }

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
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        viewBinding = true
    }
}

// build.gradle.kts
configurations.all {
    exclude(group = "com.mapbox.common", module = "common")
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
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
    implementation("androidx.core:core-splashscreen:1.0.0")

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

// Mapbox Maps SDK (use latest version; -ndk27 variant if needed)
    implementation("com.mapbox.maps:android-ndk27:11.14.4")
    // Mapbox Search (Place Autocomplete) SDK
    implementation("com.mapbox.search:autofill:2.14.0")
    implementation("com.mapbox.search:discover:2.14.0")
    implementation("com.mapbox.search:place-autocomplete:2.14.0")
    implementation("com.mapbox.search:offline:2.14.0")
    implementation("com.mapbox.search:mapbox-search-android:2.14.0")
    implementation("com.mapbox.search:mapbox-search-android-ui:2.14.0")

    implementation("com.google.android.gms:play-services-location:21.0.1")

    implementation("com.mapbox.mapboxsdk:mapbox-sdk-turf:7.0.0")
}
