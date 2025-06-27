import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jlleitschuh.gradle.ktlint")
    id("io.gitlab.arturbosch.detekt")
    id("com.github.hierynomus.license")
    id("org.jetbrains.dokka")
    id("maven-publish")
}

apply(from = "../gradle/release.gradle")

val kotlinVersion = "1.5.0"

version = "${project.properties["VERSION_NAME"]}"
group = "${project.properties["GROUP"]}"

android {
    compileSdk = 33
    namespace = "com.bugsnag.android.performance.appcompat"

    defaultConfig {
        minSdk = 17
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles.add(file("consumer-rules.pro"))
    }

    publishing {
        singleVariant("release") {
            withJavadocJar()
            withSourcesJar()
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

project.tasks.withType(KotlinCompile::class.java).configureEach {
    // Workaround for https://youtrack.jetbrains.com/issue/KT-37652
    if (name.endsWith("TestKotlin")) return@configureEach
    kotlinOptions.freeCompilerArgs += listOf("-Xexplicit-api=strict")
}

dependencies {
    api(libs.kotlin.stdlib)

    implementation(project(":bugsnag-android-performance"))
    implementation(project(":bugsnag-android-performance-impl"))

    implementation(libs.androidx.annotation)
    implementation(libs.androidx.appcompat)

    testImplementation(libs.bundles.test.jvm)
}
