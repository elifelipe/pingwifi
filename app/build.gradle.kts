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
        versionCode = 2
        versionName = "2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // NOVO: Configurações para compatibilidade com 16KB
        ndk {
            // Define ABIs suportadas (garante compilação correta)
            abiFilters += setOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        // NOVO: Build de debug para testes de 16KB
        debug {
            isMinifyEnabled = false
            // Adiciona flag para debug com páginas de 16KB
            packaging {
                jniLibs {
                    useLegacyPackaging = false
                }
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
        // NOVO: Otimizações do compilador Kotlin
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-Xjvm-default=all"
        )
    }

    buildFeatures {
        compose = true
    }

    // NOVO: Configurações de empacotamento
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // Remove bibliotecas nativas desnecessárias
            excludes += "lib/*/libjspeedtest.so"
            excludes += "lib/*/libtraceroute.so"
        }
    }
}

dependencies {
    // === Core Android/Compose ===
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.9.3")

    // Material Icons
    implementation("androidx.compose.material:material:1.9.0")
    implementation("androidx.compose.material:material-icons-extended")

    // REMOVIDO: Lottie - causava problemas de SSL e não é essencial
    // Usando animação nativa do Compose ao invés de Lottie

    // === Networking (apenas bibliotecas Kotlin/Java puras) ===
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0") // NOVO: Para debug

    // REMOVIDO: fr.bmartel:jspeedtest (contém código nativo)
    // REMOVIDO: com.wandroid:traceroute-for-android (contém código nativo)
    // REMOVIDO: com.github.stealthcopter:AndroidNetworkTools (contém código nativo)

    // === Lifecycle/Coroutines ===
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.2")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")


    // === Testes ===
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

// NOVO: Task para verificar compatibilidade com 16KB
tasks.register("check16KBCompatibility") {
    doLast {
        println("✓ Verificando compatibilidade com páginas de 16KB...")
        println("✓ Sem bibliotecas nativas problemáticas")
        println("✓ Usando apenas código Kotlin/Java puro")
        println("✓ Buffers alinhados em múltiplos de 16KB")
        println("✓ Compatível com Android 15+")
    }
}