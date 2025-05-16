Feature: Manual creation of spans

  Scenario: Manual spans can be logged
    Given I run "ManualSpanScenario"
    And I wait to receive a sampling request
    And I wait to receive a trace

    # Check the initial probability request
    Then the sampling request "Bugsnag-Span-Sampling" header equals "1:0"
    And the sampling request "Bugsnag-Api-Key" header equals "a35a2a72bd230ac0aa0f52715bbdc6aa"

    And the trace Bugsnag-Integrity header is valid
    And the trace "Bugsnag-Sent-At" header is present
    And the trace "Bugsnag-Span-Sampling" header equals "1.0:1"
    And the trace "Bugsnag-Api-Key" header equals "a35a2a72bd230ac0aa0f52715bbdc6aa"

    * a span name equals "ManualSpanScenario"
    * a span field "kind" equals 1
    * a span field "spanId" matches the regex "^[A-Fa-f0-9]{16}$"
    * a span field "traceId" matches the regex "^[A-Fa-f0-9]{32}$"
    * a span field "startTimeUnixNano" matches the regex "^[0-9]+$"
    * a span field "endTimeUnixNano" matches the regex "^[0-9]+$"
    * a span string attribute "net.host.connection.type" exists
    * every span bool attribute "bugsnag.app.in_foreground" is true

    * a span named "ManualSpanScenario" contains the attributes:
      | attribute                         | type        | value               |
      | bugsnag.span.category             | stringValue | custom              |
      | bugsnag.span.first_class          | boolValue   | true                |
      | spanStartCallback                 | boolValue   | true                |
      | spanEndCallback                   | boolValue   | true                |

    * the trace payload field "resourceSpans.0.resource" string attribute "host.arch" is one of:
      | x86   |
      | amd64 |
      | arm32 |
      | arm64 |
    * the trace payload field "resourceSpans.0.resource" string attribute "os.type" equals "linux"
    * the trace payload field "resourceSpans.0.resource" string attribute "os.name" equals "android"
    * the trace payload field "resourceSpans.0.resource" string attribute "os.version" exists
    * the trace payload field "resourceSpans.0.resource" string attribute "bugsnag.device.android_api_version" exists
    * the trace payload field "resourceSpans.0.resource" string attribute "device.id" exists
    * the trace payload field "resourceSpans.0.resource" string attribute "device.model.identifier" exists
    * the trace payload field "resourceSpans.0.resource" string attribute "device.manufacturer" exists
    * the trace payload field "resourceSpans.0.resource" string attribute "deployment.environment" is one of:
      | development |
      | production  |
    * the trace payload field "resourceSpans.0.resource" string attribute "bugsnag.app.version_code" equals "1"
    * the trace payload field "resourceSpans.0.resource" string attribute "service.version" equals "1.0"
    * the trace payload field "resourceSpans.0.resource" string attribute "service.name" equals "manual.span.service"
    * the trace payload field "resourceSpans.0.resource" string attribute "telemetry.sdk.name" equals "bugsnag.performance.android"
    * the trace payload field "resourceSpans.0.resource" string attribute "telemetry.sdk.version" matches the regex "[0-9]+\.[0-9]+\.[0-9]+"

    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0" integer attribute "bugsnag.span.callbacks_duration" is greater than 0
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0" string attribute "string" equals "test name"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0" integer attribute "longNumber" equals 1234
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0" integer attribute "intNumber" equals 5678
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0" double attribute "doubleNumber" equals 12.34
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0" boolean attribute "boolean" is false
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0" string array attribute "stringCollection" equals the array:
      | string1 |
      | string2 |
      | string3 |
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0" integer array attribute "intArray" equals the array:
      | 10 |
      | 20 |
      | 30 |
      | 40 |

  Scenario: Spans can be logged before start
    Given I run "PreStartSpansScenario"
    And I wait to receive a trace
    Then a span name equals "Post Start"
    * a span name equals "Thread Span 0"
    * a span name equals "Thread Span 1"
    * a span name equals "Thread Span 2"

  # TODO: Flaky - Pending PLAT-9364
#  @skip
#  Scenario: Span batch times out
#    Given I run "BatchTimeoutScenario"
#    And I wait to receive at least 2 spans
#    Then a span name equals "Span 1"
#    * a span name equals "Span 2"

  # Skip pending PLAT-11356
  @skip
  Scenario: Send on App backgrounded
    Given I run "AppBackgroundedScenario"
    And I send the app to the background for 5 seconds
    And I wait to receive at least 1 span
    Then a span name equals "Span 1"

  # Skip pending PLAT-11356
  @skip
  Scenario: Spans logged in the background
    Given I run "BackgroundSpanScenario"
    And I send the app to the background for 5 seconds
    And I wait to receive at least 1 span
    Then a span name equals "BackgroundSpan"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0" boolean attribute "bugsnag.app.in_foreground" is false

  Scenario: Span attributes are limited based on config
    Given I run "AttributeLimitsScenario"
    And I wait to receive at least 1 span
    Then a span name equals "Custom Span"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0" string array attribute "arrayAttribute" equals the array:
      | this is a *** 68 CHARS TRUNCATED |
    * every span string attribute "droppedAttribute" does not exist
