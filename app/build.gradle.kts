plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.anszom.rethink.setup"
    compileSdk = 36
    buildToolsVersion = "36.1.0"

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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
