plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.vaiche_driver"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.vaiche_driver"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Lấy token
        val token = (project.findProperty("MAPBOX_ACCESS_TOKEN") as String?)
            ?: System.getenv("MAPBOX_ACCESS_TOKEN") ?: ""

        // BuildConfig.MAPBOX_ACCESS_TOKEN
        buildConfigField("String", "MAPBOX_ACCESS_TOKEN", "\"$token\"")

        //  @string/mapbox_access_token
        resValue("string", "mapbox_access_token", token)
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true }

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
    }

    android {
        packaging {
            jniLibs.useLegacyPackaging = false
        }
    }
}

dependencies {

    // --- CORE & UI ---
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


    implementation("com.mapbox.maps:android:11.4.1")

    implementation("com.google.android.gms:play-services-location:21.3.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    implementation(libs.androidx.navigation.fragment.ktx)
    // --- TESTING ---
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}


