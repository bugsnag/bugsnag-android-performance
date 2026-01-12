Feature: Server responses

  Scenario: Startup P request returns 0
    Given I set the sampling probability for the next traces to "0"
    And I load scenario "GenerateSpansScenario"
    And I invoke "sendNextSpan"
    Then I should receive no traces

  Scenario: No P update: fail-retriable, fail-permanent
    Given I load scenario "GenerateSpansScenario"
    And I wait to receive a sampling request
    # 500 - Server error (retry) but still recorded by MazeRunner
    Then I set the HTTP status code for the next request to 500
    And I invoke "sendNextSpan"
    # 2 traces: 1 failed, 1 retry
    And I wait to receive 2 traces
    Then a span name equals "span 1"
    And I discard the oldest trace
    Then a span name equals "span 1"
    And I discard the oldest trace
    # 400 - Payload rejected
    Then I set the HTTP status code for the next request to 400
    And I invoke "sendNextSpan"
    And I wait to receive 1 traces
    Then a span name equals "span 2"
    # Verify that the trace is not retried
    And I discard the oldest trace
    And I should receive no traces

  Scenario: No P update: fail-permanent
    Given I load scenario "GenerateSpansScenario"
    And I wait to receive a sampling request
    Then I set the HTTP status code for the next requests to 400
    Then I invoke "sendNextSpan"
    And I wait to receive 1 trace
    # 400 - Payload rejected, no retry
    Then a span name equals "span 1"
