plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.bugsnag.benchmarks.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.bugsnag.benchmarks.android"
        minSdk = 21
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file("fakekeys.jks")
            storePassword = "password"
            keyAlias = "password"
            keyPassword = "password"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("release")
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
    kotlin {
        jvmToolchain(11)
    }
}

dependencies {
    implementation("com.bugsnag:bugsnag-android-performance:9.9.9")
    implementation("com.bugsnag:bugsnag-android-performance-impl:9.9.9")
    implementation("com.bugsnag:bugsnag-android-performance-okhttp:9.9.9")
    implementation("com.bugsnag:bugsnag-android-performance-compose:9.9.9")
    implementation("com.bugsnag:bugsnag-android-performance-appcompat:9.9.9")
    implementation("com.bugsnag:bugsnag-android-performance-coroutines:9.9.9")
    implementation("com.bugsnag:bugsnag-plugin-android-performance-named-spans:9.9.9")

    implementation(project(":benchmark-suite"))

    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
}