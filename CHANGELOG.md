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
