Feature: Manual creation of spans

    Scenario: Manual spans can be logged
    Given I run "ManualSpanScenario"
    And I wait to receive a sampling request
    And I wait to receive a trace

    # Check the initial probability request
    Then the sampling request "Bugsnag-Span-Sampling" header equals "1:0"
    And the sampling request "Bugsnag-Api-Key" header equals "a35a2a72bd230ac0aa0f52715bbdc6aa"

    And the trace "Bugsnag-Span-Sampling" header equals "1.0:1"
    And the trace "Bugsnag-Api-Key" header equals "a35a2a72bd230ac0aa0f52715bbdc6aa"

    * a span name equals "ManualSpanScenario"
    * a span field "kind" equals 1
    * a span string attribute "net.host.connection.type" exists
    * every span bool attribute "bugsnag.app.in_foreground" is true

    * a span named "ManualSpanScenario" contains the attributes:
      | attribute                         | type        | value               |
      | bugsnag.span.category             | stringValue | custom              |
      | bugsnag.span.first_class          | boolValue   | true                |
      | spanStartCallback                 | boolValue   | true                |
      | spanEndCallback                   | boolValue   | true                |

    * a trace resource string attribute "host.arch" exists
    * a trace resource string attribute "os.type" equals "linux"
    * a trace resource string attribute "os.name" equals "android"
    * a trace resource string attribute "os.version" exists
    * a trace resource string attribute "bugsnag.device.android_api_version" exists
    * a trace resource string attribute "device.id" exists
    * a trace resource string attribute "device.model.identifier" exists
    * a trace resource string attribute "device.manufacturer" exists
    * a trace resource string attribute "deployment.environment" exists
    * a trace resource string attribute "bugsnag.app.version_code" equals "1"
    * a trace resource string attribute "service.version" equals "1.0"
    * a trace resource string attribute "service.name" equals "manual.span.service"
    * a trace resource string attribute "telemetry.sdk.name" equals "bugsnag.performance.android"
    * a trace resource string attribute "telemetry.sdk.version" exists

    * a span integer attribute "bugsnag.span.callbacks_duration" is greater than 0
    * a span string attribute "string" equals "test name"
    * a span integer attribute "longNumber" equals "1234"
    * a span integer attribute "intNumber" equals "5678"
    * a span double attribute "doubleNumber" equals "12.34"
    * a span boolean attribute "boolean" equals "false"
    * a span string array attribute "stringCollection" equals the array:
      | string1 |
      | string2 |
      | string3 |
    * a span integer array attribute "intArray" equals the array:
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
    And I wait to receive a span named "Span 1"

  # Skip pending PLAT-11356
  @skip
  Scenario: Spans logged in the background
    Given I run "BackgroundSpanScenario"
    And I send the app to the background for 5 seconds
    And I wait to receive a span named "BackgroundSpan"
    Then a span name equals "BackgroundSpan"
    * a span boolean attribute "bugsnag.app.in_foreground" equals "false"

  Scenario: Span attributes are limited based on config
    Given I run "AttributeLimitsScenario"
    And I wait to receive a span named "Custom Span"
    * a span string array attribute "arrayAttribute" equals the array:
      | this is a *** 68 CHARS TRUNCATED |
    * every span string attribute "droppedAttribute" does not exist
