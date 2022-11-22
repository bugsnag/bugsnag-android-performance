plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jlleitschuh.gradle.ktlint")
    id("io.gitlab.arturbosch.detekt")
    id("maven-publish")
}

apply(from = "../gradle/release.gradle")

val kotlinVersion = "1.5.0"

version = "${project.properties["VERSION_NAME"]}"
group = "${project.properties["GROUP"]}"

android {
    compileSdk = 32

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
    api("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")

    compileOnly("com.squareup.okhttp3:okhttp:4.9.1")

    implementation(project(":bugsnag-android-performance"))
    implementation("androidx.annotation:annotation:1.3.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test.ext:junit:1.1.3")
    testImplementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
    testImplementation("org.robolectric:robolectric:4.8")
    testImplementation("org.mockito:mockito-inline:4.8.1")
    testImplementation("org.mockito.kotlin:mockito-kotlin:4.0.0")
    testImplementation("net.jimblackler.jsonschemafriend:core:0.11.4")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.9.1")
}

detekt {
    source.from(files("src/main/kotlin"))
    baseline = file("detekt-baseline.xml")
}
