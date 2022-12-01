# Bugsnag performance monitoring & reporting for Android

App performance monitoring by [Bugsnag](https://www.bugsnag.com).

## Getting started
1. [Create a Bugsnag account](https://www.bugsnag.com)
2. Add `bugsnag-android-performance` to your `build.gradle` files
```kotlin
// settings.gradle or settings.gradle.kts
dependencyResolutionManagement {
    // ...
    repositories {
        // ...
        maven { setUrl("https://jitpack.io") }
    }
}

// app/build.gradle or app/build.gradle.kts
dependencies {
    implementation("com.bugsnag:bugsnag-android-performance:0.0.0")
    // ...
}
```
3. Add your project API-KEY to your `AndroidManifest.xml`:
```xml
<application>
  <!-- ... -->
  <meta-data
    android:name="com.bugsnag.android.API_KEY"
    android:value="your-api-key-here" />
</application>
```
4. Configure and start `BugsnagPerformance` in your `Application` class
```kotlin
class MyApplication: Application() {
    override fun onCreate() {
        super.onCreate()
        BugsnagPerformance.start(PerformanceConfiguration.load(this))
        // ...
    }
}
```

These steps will automatically measure and report the app startup time, and the loading time of all your activities.  