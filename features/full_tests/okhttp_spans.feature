Feature: OkHttp EventListener

  Scenario: NetworkRequest spans are logged for requests
    Given I run "OkhttpSpanScenario"
    And I wait to receive a trace
    * a span name equals "[HTTP/GET]"
    * a span field "spanId" matches the regex "^[A-Fa-f0-9]{16}$"
    * a span field "traceId" matches the regex "^[A-Fa-f0-9]{32}$"
    * a span field "kind" equals 3
    * a span field "startTimeUnixNano" matches the regex "^[0-9]+$"
    * a span field "endTimeUnixNano" matches the regex "^[0-9]+$"
    * a span string attribute "bugsnag.span.category" equals "network"
    * a span string attribute "http.url" equals "https://google.com/?test=true"
    * a span string attribute "http.method" equals "GET"
    * a span string attribute "http.flavor" exists
    * every span integer attribute "http.response_content_length" is greater than 0
    * every span integer attribute "http.status_code" matches the regex "^[0-9]+$"
    * the trace payload field "resourceSpans.0.resource" string attribute "service.name" equals "com.bugsnag.mazeracer"
    * the trace payload field "resourceSpans.0.resource" string attribute "telemetry.sdk.name" equals "bugsnag.performance.android"
    * the trace payload field "resourceSpans.0.resource" string attribute "telemetry.sdk.version" matches the regex "[0-9]+\.[0-9]+\.[0-9]+"

  Scenario: Failed requests do not log spans
    Given I run "OkhttpSpanScenario" configured as "https://localhost:9876"
    And I wait to receive a sampling request
    Then I should receive no traces

  # TODO: Skipped on BitBar with Android 14/15 pending PLAT-15024
  @skip_bb_android_14_15
  Scenario: Auto-Instrument Network with Callback
    Given I run "OkhttpAutoInstrumentNetworkCallbackScenario"
    And I wait to receive at least 2 spans
    Then the trace "Content-Type" header equals "application/json"
    * the trace "Bugsnag-Sent-At" header matches the regex "^\d\d\d\d-\d\d-\d\dT\d\d:\d\d:\d\d\.\d\d\dZ$"
    * every span field "name" equals "[HTTP/GET]"
    * every span field "spanId" matches the regex "^[A-Fa-f0-9]{16}$"
    * every span field "traceId" matches the regex "^[A-Fa-f0-9]{32}$"
    * every span field "startTimeUnixNano" matches the regex "^[0-9]+$"
    * every span field "endTimeUnixNano" matches the regex "^[0-9]+$"
    * a span string attribute "http.url" equals "https://bugsnag.com/"
    * a span string attribute "http.url" equals "https://bugsnag.com/changed"

  # TODO: Skipped on BitBar with Android 14/15 pending PLAT-15024
  @skip_bb_android_14_15
  Scenario: Manual-Instrument Network with Callback
    Given I run "OkhttpManualNetworkCallbackScenario"
    And I wait to receive at least 2 spans
    Then the trace "Content-Type" header equals "application/json"
    * the trace "Bugsnag-Sent-At" header matches the regex "^\d\d\d\d-\d\d-\d\dT\d\d:\d\d:\d\d\.\d\d\dZ$"
    * every span field "name" equals "[HTTP/GET]"
    * every span field "spanId" matches the regex "^[A-Fa-f0-9]{16}$"
    * every span field "traceId" matches the regex "^[A-Fa-f0-9]{32}$"
    * every span field "startTimeUnixNano" matches the regex "^[0-9]+$"
    * every span field "endTimeUnixNano" matches the regex "^[0-9]+$"
    * a span string attribute "http.url" equals "https://bugsnag.com/"
    * a span string attribute "http.url" equals "https://bugsnag.com/changed"
