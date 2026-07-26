plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlinAndroidKsp)
    alias(libs.plugins.hiltAndroid)
}

android {
    namespace = "io.github.anszom.rethink.setup"
    compileSdk = 36
    buildToolsVersion = "36.1.0"

    buildFeatures {
        compose = true
    }

    defaultConfig {
        applicationId = "io.github.anszom.rethink.setup"
        minSdk = 26
        targetSdk = 36
        // On tag builds these come from the git tag via CI (see release.yml); otherwise
        // they fall back to placeholder values for local/dev builds.
        versionCode = System.getenv("VERSION_CODE")?.toInt() ?: 100
        versionName = System.getenv("VERSION_NAME") ?: "0.1"
    }

    // Release signing: the keystore file is the only secret (delivered via the
    // KEYSTORE_FILE env var, set from a GitHub secret in .github/workflows/release.yml).
    // The password is a fixed formality — it only guards the keystore file, which is
    // itself already kept secret — so it lives here in the clear, like the debug key's
    // well-known "android" password. Generate the keystore with the same values.
    val keystorePath = System.getenv("KEYSTORE_FILE")
    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = "android"
                keyAlias = "rethink"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.androidx.coreKtx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtimeKtx)
    implementation(libs.kotlinx.coroutinesAndroid)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navition.compose)
}
