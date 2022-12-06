Feature: Retries

  Scenario: Basic retry
    Given I set the HTTP status code for the next requests to "200,500"
    And I run "RetryScenario" and discard the initial p-value request
    And I wait to receive 2 traces
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 1"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.spanId" matches the regex "^[A-Fa-f0-9]{16}$"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.traceId" matches the regex "^[A-Fa-f0-9]{32}$"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.kind" equals "SPAN_KIND_INTERNAL"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.startTimeUnixNano" matches the regex "^[0-9]+$"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.endTimeUnixNano" matches the regex "^[0-9]+$"
    * the trace payload field "resourceSpans.0.resource" attribute "service.name" equals "com.bugsnag.mazeracer"
    * the trace payload field "resourceSpans.0.resource" attribute "telemetry.sdk.name" equals "bugsnag.performance.android"
    * the trace payload field "resourceSpans.0.resource" attribute "telemetry.sdk.version" equals "0.0.0"
    And I discard the oldest trace
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 1"
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.1.name" equals "Custom/span 2"

  Scenario: Retry timeout
    Given I set the HTTP status code for the next request to 500
    And I run "RetryTimeoutScenario" and discard the initial p-value request
    And I wait to receive 2 traces
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 1"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.spanId" matches the regex "^[A-Fa-f0-9]{16}$"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.traceId" matches the regex "^[A-Fa-f0-9]{32}$"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.kind" equals "SPAN_KIND_INTERNAL"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.startTimeUnixNano" matches the regex "^[0-9]+$"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.endTimeUnixNano" matches the regex "^[0-9]+$"
    * the trace payload field "resourceSpans.0.resource" attribute "service.name" equals "com.bugsnag.mazeracer"
    * the trace payload field "resourceSpans.0.resource" attribute "telemetry.sdk.name" equals "bugsnag.performance.android"
    * the trace payload field "resourceSpans.0.resource" attribute "telemetry.sdk.version" equals "0.0.0"
    And I discard the oldest trace
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 2"
