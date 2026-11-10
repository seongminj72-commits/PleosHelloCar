plugins {
    alias(libs.plugins.android.application)
}

val releaseStoreFile = providers.gradleProperty("DRIVEFLOW_RELEASE_STORE_FILE")
val releaseStorePassword = providers.gradleProperty("DRIVEFLOW_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = providers.gradleProperty("DRIVEFLOW_RELEASE_KEY_ALIAS")
val releaseKeyPassword = providers.gradleProperty("DRIVEFLOW_RELEASE_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword
).all { it.isPresent }

android {
    useLibrary("android.car")
    namespace = "com.example.pleoshellocar"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.pleoshellocar"
        minSdk = 34
        targetSdk = 37
        versionCode = 4
        versionName = "0.4.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile.get())
                storePassword = releaseStorePassword.get()
                keyAlias = releaseKeyAlias.get()
                keyPassword = releaseKeyPassword.get()
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.app.automotive)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}