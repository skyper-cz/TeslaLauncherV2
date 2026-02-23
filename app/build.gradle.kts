import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// --- 1. LOAD KEYS HERE (SAFELY OUTSIDE THE ANDROID BLOCK) ---
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}
val mapboxPublicToken = localProperties.getProperty("MAPBOX_PUBLIC_TOKEN") ?: ""
val googleMapsApiKey = localProperties.getProperty("GOOGLE_MAPS_API_KEY") ?: ""
// ---------------------------------------------------------

android {
    namespace = "com.launchers.teslalauncherv2"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.launchers.teslalauncherv2"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.4.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        resValue("string", "mapbox_access_token", mapboxPublicToken)
        manifestPlaceholders["GOOGLE_MAPS_API_KEY"] = googleMapsApiKey

        // This removes x86 and 32-bit libraries, dramatically reducing the APK size
        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a"))
        }
    }
    androidResources {
        localeFilters += listOf("en", "cs")
    }

    // HERE WE DEFINE TWO APP VERSIONS (STANDARD and AI)
    flavorDimensions += "tier"
    productFlavors {
        create("standard") {
            dimension = "tier"
            versionNameSuffix = "-standard"
        }
        create("ai") {
            dimension = "tier"
            versionNameSuffix = "-ai"
            // We can optionally change the ID to install both apps on the phone simultaneously
            // applicationIdSuffix = ".ai"
        }
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
        compose = true
        buildConfig = true
        resValues = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // --- MAPBOX ---
    implementation("com.mapbox.maps:android:11.4.0")
    implementation("com.mapbox.extension:maps-compose:11.4.0")
    implementation("com.mapbox.navigationcore:android:3.1.0")
    // --------------

    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    implementation("com.herohan:UVCAndroid:1.0.11")

    // --- GOOGLE MAPS ---
    implementation("com.google.maps.android:maps-compose:8.1.0")
    implementation("com.google.android.gms:play-services-maps:20.0.0")

    /**
    //  LiteRT - TENSORFLOW LITE (AI LIBRARIES) - These run ONLY for the "ai" build variant!
    "aiImplementation"("org.tensorflow:tensorflow-lite:2.16.1")
    "aiImplementation"("org.tensorflow:tensorflow-lite-gpu:2.16.1")
    **/
    "aiImplementation"("com.google.mlkit:text-recognition:16.0.0")


    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}