Feature: Retries

  Scenario: Basic retry
    # 200 - Get p_value, 500 - reject first payload
    Given I set the HTTP status code for the next requests to "200,500"
    And I run "RetryScenario" and discard the initial p_value
    And I wait to receive 3 traces
    # 500 - Payload rejected (but will still be tracked by MazeRunner)
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "span 1"
    And I discard the oldest trace
    # 200 - Payload delivered (retry)
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "span 1"
    And I discard the oldest trace
    # 200 - Second payload delivered
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "span 2"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.spanId" matches the regex "^[A-Fa-f0-9]{16}$"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.traceId" matches the regex "^[A-Fa-f0-9]{32}$"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.kind" equals 1
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.startTimeUnixNano" matches the regex "^[0-9]+$"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.endTimeUnixNano" matches the regex "^[0-9]+$"
    * the trace payload field "resourceSpans.0.resource" string attribute "service.name" equals "com.bugsnag.mazeracer"
    * the trace payload field "resourceSpans.0.resource" string attribute "telemetry.sdk.name" equals "bugsnag.performance.android"
    * the trace payload field "resourceSpans.0.resource" string attribute "telemetry.sdk.version" matches the regex "[0-9]+\.[0-9]+\.[0-9]+"
