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
        kotlinCompilerExtensionVersion = "1.4.1"
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
    implementation(project(":bugsnag-android-performance-impl"))
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.compose)
    implementation(libs.androidx.compose.foundation.layout)
    androidTestImplementation(libs.androidx.compose.ui.test.junit)
    androidTestImplementation(libs.bundles.test.android)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.bundles.test.jvm)
    testImplementation(libs.kotlin.reflect)

    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.material3:material3")
    androidTestImplementation("androidx.compose.material:material-icons-core")
    androidTestImplementation("androidx.activity:activity-compose:1.9.0")
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
