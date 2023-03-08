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

version = "${project.properties["VERSION_NAME"]}"
group = "${project.properties["GROUP"]}"

android {
    compileSdk = 32
    namespace = "com.bugsnag.android.performance.coroutines"

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

dependencies {
    api(libs.kotlin.stdlib)

    implementation(project(":bugsnag-android-performance"))

    implementation(libs.androidx.annotation)
    implementation(libs.kotlinx.coroutines)

    testImplementation(libs.bundles.test.jvm)

    testImplementation(libs.kotlin.reflect)
    testImplementation(libs.kotlinx.coroutines.test)
}

license {
    header = rootProject.file("LICENSE")
    ignoreFailures = true
}

downloadLicenses {
    dependencyConfiguration = "api"
}

detekt {
    source.from(files("src/main/kotlin"))
    baseline = file("detekt-baseline.xml")
}