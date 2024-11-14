## TBD

### Bug fixes

* Untagged socket violation when StrictMode is enabled https://github.com/bugsnag/bugsnag-android-performance/issues/297
  [#306](https://github.com/bugsnag/bugsnag-android-performance/pull/306)
* Fixed issue where background apps didn't consistently flush the current batch of spans
  [#305](https://github.com/bugsnag/bugsnag-android-performance/pull/305)

## 1.9.1 (2024-10-30)

### Bug fixes

* Fixed Warm AppStarts being over reported due to app-backgrounding not being fully reported internally
  [#299](https://github.com/bugsnag/bugsnag-android-performance/pull/299)
* Custom spans are reported with a "bugsnag.span.category" to match behaviour in our other SDKs
  [#300](https://github.com/bugsnag/bugsnag-android-performance/pull/300)

## 1.9.0 (2024-09-30)

### Changes

* Slow & Frozen frame metrics can optionally be reported (`PerformanceConfiguration.autoInstrumentRendering`)
  [#290](https://github.com/bugsnag/bugsnag-android-performance/pull/290)

## 1.8.0 (2024-09-26)

### Changes

* Attribute limits can now be configured in the `AndroidManifest.xml`
  [#284](https://github.com/bugsnag/bugsnag-android-performance/pull/284)

## 1.7.0 (2024-09-23)

### Bug fixes

* Avoid crashing on Android 11 due to https://issuetracker.google.com/issues/175055271.
  [#276](https://github.com/bugsnag/bugsnag-android-performance/pull/276)

### Changes

* Use API key subdomain as default Performance endpoint.
  [#277](https://github.com/bugsnag/bugsnag-android-performance/pull/277)
* Trace Propagation URLs and service name can be configured in `AndroidManifest.xml`
  [#281](https://github.com/bugsnag/bugsnag-android-performance/pull/281)
* Added configurable limit to number of span attributes per span.
  [#280](https://github.com/bugsnag/bugsnag-android-performance/pull/280)

## 1.6.0 (2024-08-27)

### Enhancements

* Custom attributes can now be set on a span, including as arrays of primitives (int, long, double, boolean, string).
  [#252](https://github.com/bugsnag/bugsnag-android-performance/pull/252)
* Introduced `OnSpanEndCallback`s that allow changes to spans when their `end()` method is called, but before they are sent.
  [#254](https://github.com/bugsnag/bugsnag-android-performance/pull/254)
* Spans in the `SpanContext` stack are now weak referenced to avoid holding spans that cannot be closed externally
  [#255](https://github.com/bugsnag/bugsnag-android-performance/pull/255)

## 1.5.0 (2024-08-01)

### Enhancements

* Add `serviceName` config option to allow the `service.name` attribute defaults to be overridden
  [258](https://github.com/bugsnag/bugsnag-android-performance/pull/258)

### Changes

* Bumped minimum Kotlin version to 1.8.0

## 1.4.0 (2024-06-27)

### Enhancements

* Added utility functions for creating `SpanOptions` while avoiding the need to reference `DEFAULTS`
  [#230](https://github.com/bugsnag/bugsnag-android-performance/pull/230)
* Set the trace/span id for the current `SpanContext` when an error is reported via `bugsnag-android`
  [#233](https://github.com/bugsnag/bugsnag-android-performance/pull/233)

## 1.3.0 (2024-05-20)

### Enhancements

* Update the `bugsnag-plugin-android-performance-okhttp` module to optionally carry the current `SpanContext` as an OpenTelemetry `traceparent` header in outgoing HTTP requests.
  [#221](https://github.com/bugsnag/bugsnag-android-performance/pull/221)
* To avoid unrealistically long ViewLoad & AppStart spans, these are discarded if the user backgrounds the app while an Activity is considered loading
  [#227](https://github.com/bugsnag/bugsnag-android-performance/pull/227)

### Bug fixes

* Remove leak mark when the Activity is on stopped since Auto-instrumented spans should only be considered “leaked” when the Activity is destroyed.
  [#210](https://github.com/bugsnag/bugsnag-android-performance/pull/210)

## 1.2.2 (2024-02-22)

### Bug fixes

* Fixed a theoretically possible `Span` leak when tracked Spans were on the context stack, and their bound tokens were garbage collected before the `Span` was closed.
  [#205](https://github.com/bugsnag/bugsnag-android-performance/pull/205)

## 1.2.1 (2024-01-09)

### Bug fixes

* Coroutines forked from other coroutines within a `BugsnagPerformanceScope` will now have spans that nest naturally
  [#193](https://github.com/bugsnag/bugsnag-android-performance/pull/193)
* Warm & Hot start spans will no longer be started before the AppStart spans
  [#196](https://github.com/bugsnag/bugsnag-android-performance/pull/196)

## 1.2.0 (2023-11-22)

### Enhancements

* Prevent configured Activities from signalling the end of an AppStart (`doNotEndAppStart` configuration option).
  [#185](https://github.com/bugsnag/bugsnag-android-performance/pull/185)
* Exclude configured Activities & Fragments from automatic instrumentation (`doNotAutoInstrument` configuration option).
  [#185](https://github.com/bugsnag/bugsnag-android-performance/pull/185)
  [#186](https://github.com/bugsnag/bugsnag-android-performance/pull/186)

## 1.1.1 (2023-10-16)

### Bug fixes

* os.version is now correctly reported as the release version (12, 13, 14), sdk level is reported in a new resource attribute
  [#178](https://github.com/bugsnag/bugsnag-android-performance/pull/178)
* AppStart spans are now platform-prefixed (eg: AppStart/AndroidCold) to differentiate different AppStart layers
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
