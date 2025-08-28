plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.elftech.pingwifi"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.elftech.pingwifi"
        minSdk = 24
        targetSdk = 36
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
        compose = true
    }
}

dependencies {
    // --- AndroidX/Compose padrão (já estavam) ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation("androidx.navigation:navigation-compose:2.9.3")

    implementation("androidx.compose.material:material:1.9.0")

    implementation("androidx.compose.material:material-icons-extended")

    implementation("com.github.LottieFiles:dotlottie-android:0.9.2")
    // --- Networking / utilitários que você já usava ---
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("fr.bmartel:jspeedtest:1.32.1")
    implementation("com.wandroid:traceroute-for-android:1.0.0")
    implementation("com.github.stealthcopter:AndroidNetworkTools:0.4.5.3")

    // --- Lifecycle Compose helpers (você já tinha) ---
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")

    // === NOVO: Coroutines (necessário para StateFlow) ===
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // === NOVO: ViewModel KTX (fornece viewModelScope) ===
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.2")
}
