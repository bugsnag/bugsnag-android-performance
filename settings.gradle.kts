pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { setUrl("https://jitpack.io") }
    }
}

rootProject.name = "bugsnag-android-performance"

include(
    ":bugsnag-android-performance",
    ":bugsnag-plugin-android-performance-okhttp",
    ":bugsnag-plugin-android-performance-named-spans",
    ":benchmarks",
)
include(":bugsnag-android-performance-api")
include(":bugsnag-android-performance-impl")
include(":bugsnag-android-performance-coroutines")
include(":bugsnag-android-performance-appcompat")
include(":bugsnag-android-performance-compose")
include(":bugsnag-android-performance-okhttp")
