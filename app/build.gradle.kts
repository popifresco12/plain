import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.plain.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.plain.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 20
        versionName = "0.2.0"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            // Leer desde local.properties (NO COMMITEAR al repo)
            val props = Properties()
            val propsFile = rootProject.file("local.properties")
            if (propsFile.exists()) {
                props.load(propsFile.inputStream())
                storeFile = file(props.getProperty("RELEASE_STORE_FILE", "../plain-release-key.jks"))
                storePassword = props.getProperty("RELEASE_STORE_PASSWORD", "")
                keyAlias = props.getProperty("RELEASE_KEY_ALIAS", "plain")
                keyPassword = props.getProperty("RELEASE_KEY_PASSWORD", "")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
            // Sin logging en release
        }
        debug {
            // Solo debug
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // ViewModel para Compose (obligatorio para refactor)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // Biometric
    implementation("androidx.biometric:biometric:1.2.0-alpha05")

    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.2.0")
    implementation("androidx.compose.material:material-icons-extended")

    // Swipe cards
    implementation("androidx.compose.animation:animation")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.6")

    // Coil para imágenes de planes
    implementation("io.coil-kt:coil-compose:2.5.0")

    // Retrofit (API client)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Logging interceptor solo en debug
    debugImplementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // DataStore (token storage)
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
