import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// --- 1. ZDE NAČTEME KLÍČE (BEZPEČNĚ MIMO ANDROID BLOK) ---
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}
// Načteme token do proměnné (pokud chybí, bude prázdná)
val mapboxPublicToken = localProperties.getProperty("MAPBOX_PUBLIC_TOKEN") ?: ""
val googleMapsApiKey = localProperties.getProperty("GOOGLE_MAPS_API_KEY") ?: ""
// ---------------------------------------------------------

android {
    namespace = "com.launchers.teslalauncherv2"
    compileSdk = 36 // Opravena syntaxe verze

    defaultConfig {
        applicationId = "com.launchers.teslalauncherv2"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // --- 2. ZDE UŽ JEN PŘEDÁME HOTOVOU PROMĚNNOU ---
        // Vloží token do resources, takže v aplikaci bude dostupný jako R.string.mapbox_access_token
        resValue("string", "mapbox_access_token", mapboxPublicToken)

        // 2. PŘIDÁNO: Pošle klíč přímo do AndroidManifestu! Tohle ti tam chybí.
        manifestPlaceholders["GOOGLE_MAPS_API_KEY"] = googleMapsApiKey
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

    // --- MAPBOX (OPRAVENO: PŘÍMÉ VERZE MÍSTO BOM) ---
    implementation("com.mapbox.maps:android:11.4.0")
    implementation("com.mapbox.extension:maps-compose:11.4.0")

    // 3. Jádro navigace v3 (Nové Group ID pro v3)
    implementation("com.mapbox.navigationcore:android:3.1.0")
    // ------------------------------------------------

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.github.jiangdongguo:AndroidUSBCamera:2.3.4")

    // Google Maps pro Compose
    implementation("com.google.maps.android:maps-compose:4.4.1")
    // Služby Google Play (obsahuje nástroj na detekci GMS)
    implementation("com.google.android.gms:play-services-maps:19.0.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}