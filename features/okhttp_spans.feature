Feature: Manual creation of spans

  Scenario: Manual spans can be logged
    Given I run "OkhttpSpanScenario" and discard the initial p-value request
    And I wait to receive 1 traces
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "[HTTP/GET]"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.spanId" matches the regex "^[A-Fa-f0-9]{16}$"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.traceId" matches the regex "^[A-Fa-f0-9]{32}$"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.kind" equals 3
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.startTimeUnixNano" matches the regex "^[0-9]+$"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.endTimeUnixNano" matches the regex "^[0-9]+$"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0" string attribute "bugsnag.span.category" equals "network"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0" string attribute "http.url" equals "https://google.com/?test=true"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0" string attribute "http.method" equals "GET"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0" string attribute "http.flavor" exists
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0" integer attribute "http.status_code" matches the regex "^[0-9]+$"
    * the trace payload field "resourceSpans.0.resource" string attribute "service.name" equals "com.bugsnag.mazeracer"
    * the trace payload field "resourceSpans.0.resource" string attribute "telemetry.sdk.name" equals "bugsnag.performance.android"
    * the trace payload field "resourceSpans.0.resource" string attribute "telemetry.sdk.version" matches the regex "[0-9]+\.[0-9]+\.[0-9]+"
