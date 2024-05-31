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

version = "${project.properties["VERSION_NAME"]}"
group = "${project.properties["GROUP"]}"

android {
    compileSdk = 34
    namespace = "com.bugsnag.android.performance.compose"

    defaultConfig {
        minSdk = 21
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

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.13"
    }
}

project.tasks.withType(KotlinCompile::class.java).configureEach {
    // Workaround for https://youtrack.jetbrains.com/issue/KT-37652
    if (name.endsWith("TestKotlin")) return@configureEach
    kotlinOptions.freeCompilerArgs += listOf("-Xexplicit-api=strict")
}

dependencies {
    api(libs.kotlin.stdlib)

    compileOnly(libs.okhttp)

    implementation(project(":bugsnag-android-performance"))
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.compose)

    testImplementation(libs.bundles.test.jvm)
    testImplementation(libs.kotlin.reflect)
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