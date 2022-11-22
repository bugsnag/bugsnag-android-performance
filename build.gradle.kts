// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.library") version "7.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.5.0" apply false
    id("org.jlleitschuh.gradle.ktlint") version "10.1.0" apply false
    id("io.gitlab.arturbosch.detekt") version "1.21.0" apply false
    id("org.jetbrains.dokka") version "1.7.20" apply false
}

tasks.create("clean") {
    doLast {
        delete(rootProject.buildDir)
    }
}
