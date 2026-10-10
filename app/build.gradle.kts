plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.bantai"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bantai"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        release {
            // Para sa judges: sideload na APK, kaya debug key muna ang pirma.
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // Release lang ang may Gemma sa loob (~584 MB). Ilagay ang model sa ../models/ (naka-gitignore).
    // Ang debug ay kumukuha pa rin sa /data/local/tmp/llm para mabilis ang install habang nagde-develop.
    sourceSets.getByName("release").assets.srcDir("../models")
    androidResources {
        noCompress += "litertlm"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // LiteRT LM
    implementation(libs.litertlm.android)
    implementation(libs.kotlinx.coroutines.android)

    // AndroidX & Material UI
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    // Frosted glass blur (iOS-style) para sa floating navbar.
    implementation("com.github.Dimezis:BlurView:version-3.2.0")

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
}
