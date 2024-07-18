Feature: Manual creation of spans

Scenario: Errors notified within a span include the correlation data
  Given I run "CorrelatedErrorScenario"
  And I wait to receive a trace
  And I wait to receive an error

  * a span name equals "CorrelatedError Span"
  * a span field "kind" equals 1
  * a span field "spanId" matches the regex "^[A-Fa-f0-9]{16}$"
  * a span field "traceId" matches the regex "^[A-Fa-f0-9]{32}$"
  * a span field "startTimeUnixNano" matches the regex "^[0-9]+$"
  * a span field "endTimeUnixNano" matches the regex "^[0-9]+$"

  # Check the error correlation with the span
  * the event "correlation.traceId" is not null
  * the event "correlation.spanId" is not null
