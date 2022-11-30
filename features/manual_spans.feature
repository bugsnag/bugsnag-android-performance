Feature: Manual creation of spans

  Scenario: Manual spans can be logged
    Given I run "ManualSpanScenario"
    And I wait to receive 1 traces
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/ManualSpanScenario"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.spanId" matches the regex "^[A-Fa-f0-9]{16}$"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.traceId" matches the regex "^[A-Fa-f0-9]{32}$"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.kind" equals "SPAN_KIND_INTERNAL"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.startTimeUnixNano" matches the regex "^[0-9]+$"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.endTimeUnixNano" matches the regex "^[0-9]+$"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0" attribute "net.host.connection.type" exists
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0" attribute "bugsnag.app.in_foreground" is true

    * the trace payload field "resourceSpans.0.resource" attribute "host.arch" is one of:
      | x86   |
      | amd64 |
      | arm32 |
      | arm64 |
    * the trace payload field "resourceSpans.0.resource" attribute "os.type" equals "linux"
    * the trace payload field "resourceSpans.0.resource" attribute "os.name" equals "android"
    * the trace payload field "resourceSpans.0.resource" attribute "os.version" exists
    * the trace payload field "resourceSpans.0.resource" attribute "device.model.identifier" exists
    * the trace payload field "resourceSpans.0.resource" attribute "device.manufacturer" exists
    * the trace payload field "resourceSpans.0.resource" attribute "deployment.environment" is one of:
      | development |
      | production  |
    * the trace payload field "resourceSpans.0.resource" attribute "bugsnag.app.version_code" is 1
    * the trace payload field "resourceSpans.0.resource" attribute "service.name" equals "com.bugsnag.mazeracer"
    * the trace payload field "resourceSpans.0.resource" attribute "telemetry.sdk.name" equals "bugsnag.performance.android"
    * the trace payload field "resourceSpans.0.resource" attribute "telemetry.sdk.version" equals "0.0.0"

  Scenario: Spans can be logged before start
    Given I run "PreStartSpansScenario"
    And I wait to receive a trace
    Then a span name equals "Custom/Post Start"
    * a span name equals "Custom/Thread Span 0"
    * a span name equals "Custom/Thread Span 1"
    * a span name equals "Custom/Thread Span 2"

  Scenario: Span batch times out
    Given I run "BatchTimeoutScenario"
    And I wait to receive 1 traces
    Then a span name equals "Custom/Span 1"
    * a span name equals "Custom/Span 2"

  Scenario: Send on App backgrounded
    Given I run "AppBackgroundedScenario"
    And I send the app to the background for 5 seconds
    And I wait to receive 1 traces
    Then a span name equals "Custom/Span 1"

  Scenario: Spans logged in the background
    Given I run "BackgroundSpanScenario"
    And I send the app to the background for 5 seconds
    And I wait to receive 1 traces
    Then a span name equals "Custom/BackgroundSpan"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0" attribute "bugsnag.app.in_foreground" is false
