Feature: Server responses

  Scenario: No P update: success, fail-permanent, fail-retriable
    Given I set the HTTP status code for the next requests to 200,200
    And I load scenario "GenerateSpansScenario"
    And I wait to receive a sampling request
    Then I invoke "sendNextSpan"
    And I wait to receive 1 trace
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "span 12"
    Then I discard the oldest trace
    Given I set the HTTP status code for the next requests to 400
    Then I invoke "sendNextSpan"
    And I wait to receive 1 trace
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "span 2"
    Then I discard the oldest trace
    Given I set the HTTP status code for the next requests to 500,200
    Then I invoke "sendNextSpan"
    And I wait to receive 2 traces
    # we expect a failure, and a retry
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "span 3"
    Then I discard the oldest trace
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "span 3"
