
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val tmdbBearerToken = providers.gradleProperty("TMDB_BEARER_TOKEN")
    .orElse(providers.environmentVariable("TMDB_BEARER_TOKEN"))
    .orElse("")
    .get()

android {
    namespace = "com.cinema"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cinema"
        minSdk = 24
        targetSdk = 35

        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "TMDB_BEARER_TOKEN",
            "\"${tmdbBearerToken.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        )
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

    // Android Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.activity.compose)

    // Jetpack Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)

    // Material Icons - Search, Close and other icons
    implementation("androidx.compose.material:material-icons-extended")

    // ViewModel
    implementation(libs.androidx.lifecycle.viewmodel)

    // Navigation
    implementation(libs.navigation.compose)

    // JSON
    implementation(libs.gson)

    // Retrofit
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)

    // OkHttp
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Coroutines
    implementation(libs.coroutines.android)

    // Encrypted preferences
    implementation(libs.security.crypto)

    // Media3 Player
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    implementation("androidx.media3:media3-common:1.3.1")

    // Coil
    implementation(libs.coil.compose)

    // Debug tools
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Unit tests
    testImplementation("junit:junit:4.13.2")

    // Android tests
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
