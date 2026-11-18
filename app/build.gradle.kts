plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.cinema"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cinema"
        minSdk = 24
        targetSdk = 35

        versionCode = 1
        versionName = "1.0"

        // Default TMDB credential (v3 API key). Override without editing code via
        // -PTMDB_API_KEY=... or the TMDB_API_KEY environment variable. The user can
        // still replace it at runtime from the in-app settings.
        val tmdbKey = (providers.gradleProperty("TMDB_API_KEY").orNull
            ?: System.getenv("TMDB_API_KEY")
            ?: "b8682ea087c2699ba1eb9caab08d9077")
        buildConfigField("String", "TMDB_DEFAULT_KEY", "\"$tmdbKey\"")
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
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Android core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    // Navigation
    implementation(libs.navigation.compose)

    // Network + JSON
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.gson)

    // Coroutines
    implementation(libs.coroutines.android)

    // Images
    implementation(libs.coil.compose)

    debugImplementation("androidx.compose.ui:ui-tooling")
}
