Feature: Manual creation of spans

  Scenario: Manual spans can be logged
    Given I run "OkhttpSpanScenario"
    And I wait to receive 1 traces
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "HTTP/GET"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.spanId" matches the regex "^[A-Fa-f0-9]{16}$"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.traceId" matches the regex "^[A-Fa-f0-9]{32}$"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.kind" equals "SPAN_KIND_CLIENT"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.startTimeUnixNano" matches the regex "^[0-9]+$"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.endTimeUnixNano" matches the regex "^[0-9]+$"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0" attribute "bugsnag.span_category" equals "network"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0" attribute "http.url" equals "https://google.com/?test=true"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0" attribute "http.method" equals "GET"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0" attribute "http.flavor" exists
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0" attribute "http.status_code" matches the regex "^[0-9]+$"
    * the trace payload field "resourceSpans.0.resource" attribute "service.name" equals "com.bugsnag.mazeracer"
    * the trace payload field "resourceSpans.0.resource" attribute "telemetry.sdk.name" equals "bugsnag.performance.android"
    * the trace payload field "resourceSpans.0.resource" attribute "telemetry.sdk.version" equals "0.0.0"
