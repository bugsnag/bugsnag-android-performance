## 1.2.0 (2023-11-21)

### Enhancements

* Prevent configured Activities from signalling the end of an App Start (`doNotEndAppStart`
  configuration option).
* Exclude configured Activities from automatic instrumentation (`doNotAutoInstrument` configuration
  option).

## 1.1.1 (2023-10-16)

### Bug fixes

* os.version is now correctly reported as the release version (12, 13, 14), sdk level is reported in
  a new resource attribute
  [#178](https://github.com/bugsnag/bugsnag-android-performance/pull/178)
* AppStart spans are now platform-prefixed (eg: AppStart/AndroidCold) to differentiate different
  AppStart layers
  [#179](https://github.com/bugsnag/bugsnag-android-performance/pull/179)

## 1.1.0 (2023-09-06)

### Enhancements

* Network request spans can now be controlled/modified via configurable callback.
  [#171](https://github.com/bugsnag/bugsnag-android-performance/pull/171)
* AppStartPhase/Framework introduced to mark the time between class loading & Application.onCreate
  [#163](https://github.com/bugsnag/bugsnag-android-performance/pull/163)
* Support for OkHttp 5.0.0 in [bugsnag-plugin-android-performance-okhttp](bugsnag-plugin-android-performance-okhttp)
  [#167](https://github.com/bugsnag/bugsnag-android-performance/pull/167)

### Bug fixes

* Traces are now correctly enqueued for retry when there is no active network (instead of being swallowed by the `UnknownHostException`)
  [#170](https://github.com/bugsnag/bugsnag-android-performance/pull/170)
* Delivery and retry are only considered to have done work if the payload was delivered successfully
  [#172](https://github.com/bugsnag/bugsnag-android-performance/pull/172)

## 1.0.0 (2023-07-17)

### Bug fixes

* Fragments added in the same FragmentTransaction won't unexpectedly nest their ViewLoad spans
  [#159](https://github.com/bugsnag/bugsnag-android-performance/pull/159)

## 0.1.8 (2023-06-26)

### Changes

* *Breaking change*: removed the `samplingProbability` configuration option
  [#149](https://github.com/bugsnag/bugsnag-android-performance/pull/149)

### Enhancements

* ApiKey can be read from "com.bugsnag.performance.android.API_KEY" so that `bugsnag-android` and `bugsnag-android-performance` can have different ApiKeys in the manifest.
  [#152](https://github.com/bugsnag/bugsnag-android-performance/pull/152)
* AppStart spans now end strictly when the first ViewLoad ends, allowing manual control of the AppStart end (when combined with `PerformanceConfiguration.autoInstrumentActivities`)
  [#154](https://github.com/bugsnag/bugsnag-android-performance/pull/154)

### Bug fixes

* More reliably report the response Content-Length of HTTP requests
  [#150](https://github.com/bugsnag/bugsnag-android-performance/pull/150)

## 0.1.7 (2023-06-21)

### Enhancements

* Report the `device.id` using to the same mechanism used by `bugsnag-android`
  [#142](https://github.com/bugsnag/bugsnag-android-performance/pull/142)

### Bug fixes

* Activities that call finish() from onCreate() will no longer leak ViewLoad spans
  [#144](https://github.com/bugsnag/bugsnag-android-performance/pull/144)

## 0.1.6 (2023-06-13)

### Enhancements

* Each trace reported will include the current clock-time to allow the server-side to adjust device clocks
  [#126](https://github.com/bugsnag/bugsnag-android-performance/pull/126)

### Bug fixes

* `bugsnag-plugin-android-performance-okhttp` will now discard NetworkRequest spans when the request is cancelled or fails
  [#123](https://github.com/bugsnag/bugsnag-android-performance/pull/123)
* Default to using the GNSS clock (if available) to attempt to avoid problems with clocks which could lead to negative timestamps on Spans
  [#124](https://github.com/bugsnag/bugsnag-android-performance/pull/124)
* Map "CDMA - 1xRTT" network subtype to the expected "cdma2000_1xrtt" value
  [#134](https://github.com/bugsnag/bugsnag-android-performance/pull/134)
* Background AppStart spans (for broadcasts & services) are now discarded to avoid skewing AppStart/Cold metrics
  [#135](https://github.com/bugsnag/bugsnag-android-performance/pull/135)

## 0.1.5 (2023-04-25)

### Bug fixes

* `service.version` is now correctly reported as the app versionName
  [#113](https://github.com/bugsnag/bugsnag-android-performance/pull/113)
* ViewLoad spans should only be marked "first class" when there are no other ViewLoad spans in the SpanContext
  [#115](https://github.com/bugsnag/bugsnag-android-performance/pull/115)
* The "first view" reported as part of AppStart spans is based on the first ViewLoad started rather than the first Activity resumed
  [#119](https://github.com/bugsnag/bugsnag-android-performance/pull/119)
* Fixed the reporting of cellular network subtypes (when the app has appropriate permissions)
  [#116](https://github.com/bugsnag/bugsnag-android-performance/pull/116)

## 0.1.4 (2023-04-11)

### Breaking changes
The following changes need attention when updating to this version of the library:

- Applied updated span and attribute naming (causes duplicate aggregations in your dashboard of App Start, Screen Load and Network spans from previous versions)
  [#106](https://github.com/bugsnag/bugsnag-android-performance/pull/106)

### Enhancements

* Added HTTP attribute support for manual NetworkRequest spans
  [#110](https://github.com/bugsnag/bugsnag-android-performance/pull/110)

### Bug fixes

* Fixed the null-pointer warning in Tracer when dealing with empty batches of spans
  [#107](https://github.com/bugsnag/bugsnag-android-performance/pull/107)
* Observed non-OpenTelemetry network subtypes are mapped to known values
  [#108](https://github.com/bugsnag/bugsnag-android-performance/pull/108)

## 0.1.3 (2023-03-28)

### Enhancements

* Reduced the amount of noise in the default logging, and use the NoopLogger by default in "production" releaseStage
  [#101](https://github.com/bugsnag/bugsnag-android-performance/pull/101)

### Bug fixes

* Corrected the `first_view_name` attribute in `AppStart` spans
  [#99](https://github.com/bugsnag/bugsnag-android-performance/pull/99)
* Initial probability request now correctly sends the `Bugsnag-Span-Sampling` header
  [#103](https://github.com/bugsnag/bugsnag-android-performance/pull/103)

## 0.1.2 (2023-03-15)

### Bug fixes

* PerformanceConfiguration.enabledReleaseStages now defaults to `null` effectively enabling all release stages (and follows the logic in `bugsnag-android`)
  [#93](https://github.com/bugsnag/bugsnag-android-performance/pull/93)

## 0.1.1 (2023-03-15)

* Invalid api-keys are logged as warnings instead of failing startup
  [#86](https://github.com/bugsnag/bugsnag-android-performance/pull/86)
* Removed the "Custom" prefix from custom span names
  [#84](https://github.com/bugsnag/bugsnag-android-performance/pull/84)
* Encode SpanKind as OpenTelemetry compliant integers instead of strings
  [#83](https://github.com/bugsnag/bugsnag-android-performance/pull/83)
* Fixed `PerformanceConfiguration.enabledReleaseStage`
  [#81](https://github.com/bugsnag/bugsnag-android-performance/pull/81)
* Fragment load instrumentation (via `bugsnag-plugin-android-performance-appcompat`)
  [#79](https://github.com/bugsnag/bugsnag-android-performance/pull/79)
* Support measurement of Kotlin coroutines (via `bugsnag-plugin-android-performance-coroutines`)
  [#78](https://github.com/bugsnag/bugsnag-android-performance/pull/78)

## 0.1.0 (27-02-2023)

Preview release.
