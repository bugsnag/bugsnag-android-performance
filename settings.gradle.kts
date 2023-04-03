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
    ":bugsnag-plugin-android-performance-appcompat",
    ":bugsnag-plugin-android-performance-coroutines",

    ":benchmarks",
)
