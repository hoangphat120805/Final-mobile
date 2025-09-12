// app/build.gradle.kts
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.vaiche_driver"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.vaiche_driver"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Lấy Mapbox public token từ gradle.properties (MAPBOX_ACCESS_TOKEN) hoặc env
        val token = (project.findProperty("MAPBOX_ACCESS_TOKEN") as String?)
            ?: System.getenv("MAPBOX_ACCESS_TOKEN")
            ?: ""

        // BuildConfig.MAPBOX_ACCESS_TOKEN
        buildConfigField("String", "MAPBOX_ACCESS_TOKEN", "\"$token\"")
        // @string/mapbox_access_token
        resValue("string", "mapbox_access_token", token)


    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // để log dễ hơn, có thể bật shrinkResources=false nếu cần
            isMinifyEnabled = false
        }
    }

    packaging {
        // Tránh xung đột jni .so (Nav v3 đã kéo đúng Maps SDK, không cần add thêm)
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    // --- CORE & UI (dùng Version Catalog nếu có) ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // --- ARCHITECTURE COMPONENTS (MVVM) ---
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.1")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.1")
    implementation("androidx.fragment:fragment-ktx:1.7.1")

    // --- COROUTINES ---
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // --- NETWORKING ---
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")

    // --- IMAGE LOADING ---
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // --- OPTIMIZATION ---
    implementation("androidx.startup:startup-runtime:1.1.1")
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")

    // --- ANDROIDX NAVIGATION (fragment) ---
    implementation(libs.androidx.navigation.fragment.ktx)

    // Mapbox Navigation v3.11.7 (NDK27 – hỗ trợ 16KB page size)
    implementation("com.mapbox.navigationcore:android-ndk27:3.11.7")
    implementation("com.mapbox.navigationcore:ui-maps-ndk27:3.11.7")
    implementation("com.mapbox.navigationcore:tripdata-ndk27:3.11.7")
    implementation("com.mapbox.navigationcore:voice-ndk27:3.11.7")
    implementation("com.mapbox.navigationcore:ui-components-ndk27:3.11.7")

    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // --- TESTING ---
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
