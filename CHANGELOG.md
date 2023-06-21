## TBD

### Changes

* *Breaking change*: removed the `samplingProbability` configuration option
  [#149](https://github.com/bugsnag/bugsnag-android-performance/pull/149)

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
