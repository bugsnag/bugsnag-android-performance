Feature: Manual creation of spans

Scenario: Errors notified within a span include the correlation data
  Given I run "CorrelatedErrorScenario"
  And I wait to receive a span named "CorrelatedError Span"
  And I wait to receive an error

  * a span field "kind" equals 1

  # Check the error correlation with the span
  * the event "correlation.traceId" is not null
  * the event "correlation.spanId" is not null
