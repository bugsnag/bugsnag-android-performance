plugins {
    alias(libs.plugins.agp.library)
    alias(libs.plugins.benchmark)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.licenseCheck)
}

android {
    namespace = "com.bugsnag.benchmarks"
    compileSdk = 33

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    defaultConfig {
        minSdk = 23
        targetSdk = 33

        testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"

        // If you want relative method costs, uncomment the appropriate instrumentation option:
        // testInstrumentationRunnerArguments["androidx.benchmark.profiling.mode"] = "StackSampling"
        // testInstrumentationRunnerArguments["androidx.benchmark.profiling.mode"] = "MethodTracing"
    }

    testBuildType = "release"
    buildTypes {
        debug {
        }
        release {
            isDefault = true
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "benchmark-proguard-rules.pro",
            )
        }
    }
}

dependencies {
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.benchmark)

    androidTestImplementation(project(":bugsnag-android-performance"))
}
