Feature: Manual creation of spans

  Scenario: Manual spans can be logged
    Given I run "ManualSpanScenario"
    And I wait to receive a p_value
    And I wait to receive a trace

    # Check the initial probability request
    Then the p_value "Bugsnag-Span-Sampling" header equals "1:0"
    And the p_value "Bugsnag-Api-Key" header equals "a35a2a72bd230ac0aa0f52715bbdc6aa"

    And the trace Bugsnag-Integrity header is valid
    And the trace "Bugsnag-Sent-At" header is not null
    And the trace "Bugsnag-Span-Sampling" header equals "1.0:1"
    And the trace "Bugsnag-Api-Key" header equals "a35a2a72bd230ac0aa0f52715bbdc6aa"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "ManualSpanScenario"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.spanId" matches the regex "^[A-Fa-f0-9]{16}$"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.traceId" matches the regex "^[A-Fa-f0-9]{32}$"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.kind" equals 1
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.startTimeUnixNano" matches the regex "^[0-9]+$"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.endTimeUnixNano" matches the regex "^[0-9]+$"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0" string attribute "net.host.connection.type" exists
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0" boolean attribute "bugsnag.app.in_foreground" is true

    * the trace payload field "resourceSpans.0.resource" string attribute "host.arch" is one of:
      | x86   |
      | amd64 |
      | arm32 |
      | arm64 |
    * the trace payload field "resourceSpans.0.resource" string attribute "os.type" equals "linux"
    * the trace payload field "resourceSpans.0.resource" string attribute "os.name" equals "android"
    * the trace payload field "resourceSpans.0.resource" string attribute "os.version" exists
    * the trace payload field "resourceSpans.0.resource" string attribute "device.model.identifier" exists
    * the trace payload field "resourceSpans.0.resource" string attribute "device.manufacturer" exists
    * the trace payload field "resourceSpans.0.resource" string attribute "deployment.environment" is one of:
      | development |
      | production  |
    * the trace payload field "resourceSpans.0.resource" string attribute "bugsnag.app.version_code" equals "1"
    * the trace payload field "resourceSpans.0.resource" string attribute "service.version" equals "1.0"
    * the trace payload field "resourceSpans.0.resource" string attribute "service.name" equals "com.bugsnag.mazeracer"
    * the trace payload field "resourceSpans.0.resource" string attribute "telemetry.sdk.name" equals "bugsnag.performance.android"
    * the trace payload field "resourceSpans.0.resource" string attribute "telemetry.sdk.version" matches the regex "[0-9]+\.[0-9]+\.[0-9]+"

  Scenario: Spans can be logged before start
    Given I run "PreStartSpansScenario" and discard the initial p_value
    And I wait to receive a trace
    Then a span name equals "Post Start"
    * a span name equals "Thread Span 0"
    * a span name equals "Thread Span 1"
    * a span name equals "Thread Span 2"

  # TODO: Flaky - Pending PLAT-9364
  @skip
  Scenario: Span batch times out
    Given I run "BatchTimeoutScenario" and discard the initial p_value
    And I wait to receive at least 2 spans
    Then a span name equals "Span 1"
    * a span name equals "Span 2"

  Scenario: Send on App backgrounded
    Given I run "AppBackgroundedScenario" and discard the initial p_value
    And I send the app to the background for 5 seconds
    And I wait for 1 span
    Then a span name equals "Span 1"

  Scenario: Spans logged in the background
    Given I run "BackgroundSpanScenario" and discard the initial p_value
    And I send the app to the background for 5 seconds
    And I wait for 1 span
    Then a span name equals "BackgroundSpan"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0" boolean attribute "bugsnag.app.in_foreground" is false
