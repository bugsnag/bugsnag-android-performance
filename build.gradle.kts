// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.agp.library) apply false
    alias(libs.plugins.benchmark) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.licenseCheck) apply false
    alias(libs.plugins.binaryCompatibility) apply false
}

tasks.create("clean") {
    doLast {
        delete(rootProject.layout.buildDirectory)
    }
}
