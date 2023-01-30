Feature: Server responses

  Scenario: Startup P request returns 0
    Given I set the sampling probability for the next traces to "0"
    And I run "ThreeSpansScenario" and discard the initial p-value request
    And I should receive no traces

  Scenario: No P update: success, fail-permanent, fail-retriable
    Given I set the HTTP status code for the next requests to "200,200,400,500"
    And I run "ThreeSpansScenario" and discard the initial p-value request
    And I wait to receive at least 3 traces
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 1"
    And I discard the oldest trace
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 2"
    And I discard the oldest trace
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 3"

  Scenario: No P update: success, fail-retriable, fail-permanent
    Given I set the HTTP status code for the next requests to "200,200,500,400"
    And I run "ThreeSpansScenario" and discard the initial p-value request
    And I wait to receive 3 traces
    # 200 - Payload accepted
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 1"
    And I discard the oldest trace
    # 500 - Server error (retry) but still recorded by MazeRunner
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 2"
    And I discard the oldest trace
    # Retry of the previous request
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 2"

  Scenario: No P update: fail-retriable, fail-permanent, success
    Given I set the HTTP status code for the next requests to "200,500,400,200"
    And I run "ThreeSpansScenario" and discard the initial p-value request
    And I wait to receive 3 traces
    # 500 - Server error (retry) but still recorded by MazeRunner
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 1"
    And I discard the oldest trace
    # 400 - Retry, payload rejected (no retry)
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 1"
    And I discard the oldest trace
    # 200 - Payload accepted
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 2"

  Scenario: No P update: fail-retriable, success, fail-permanent
    Given I set the HTTP status code for the next requests to "200,500,200,400"
    And I run "ThreeSpansScenario" and discard the initial p-value request
    And I wait to receive 3 traces
    # 500 - Server error (retry) but still recorded by MazeRunner
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 1"
    And I discard the oldest trace
    # 200 - Payload accepted
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 1"
    And I discard the oldest trace
    # 400 - Payload rejected
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 2"

  Scenario: No P update: fail-permanent, fail-retriable, success
    Given I set the HTTP status code for the next requests to "200,400,500,200"
    And I run "ThreeSpansScenario" and discard the initial p-value request
    And I wait to receive 3 traces
    # 400 - Payload rejected, no retry
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 1"
    And I discard the oldest trace
    # 500 - Server error (retry) but still recorded by MazeRunner
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 2"
    And I discard the oldest trace
    # Retry of the previous request
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 2"

  Scenario: No P update: fail-permanent, success, fail-retriable
    Given I set the HTTP status code for the next requests to "200,400,200,500"
    And I run "ThreeSpansScenario" and discard the initial p-value request
    And I wait to receive 4 traces
    # 400 - Payload rejected, no retry
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 1"
    And I discard the oldest trace
    # 200 - Payload accepted
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 2"
    And I discard the oldest trace
    # 500 - Payload rejected (retry)
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 3"
    And I discard the oldest trace
    # Retry of the previous request
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 3"

  # P=0 on first response

  Scenario: Update P to 0 on first response: success, fail-permanent, fail-retriable
    Given I set the HTTP status code for the next requests to "200,200,400,500"
    Given I set the sampling probability for the next traces to "1,0"
    And I run "ThreeSpansScenario" and discard the initial p-value request
    And I wait for 1 span
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 1"

  Scenario: Update P to 0 on first response: success, fail-retriable, fail-permanent
    Given I set the HTTP status code for the next requests to "200,200,500,400"
    Given I set the sampling probability for the next traces to "1,0"
    And I run "ThreeSpansScenario" and discard the initial p-value request
    And I wait to receive at least 1 traces
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 1"

  Scenario: Update P to 0 on first response: fail-retriable, fail-permanent, success
    Given I set the HTTP status code for the next requests to "200,500,400,200"
    Given I set the sampling probability for the next traces to "1,0"
    And I run "ThreeSpansScenario" and discard the initial p-value request
    And I wait to receive at least 1 traces
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 1"

  Scenario: Update P to 0 on first response: fail-retriable, success, fail-permanent
    Given I set the HTTP status code for the next requests to "200,500,200,400"
    Given I set the sampling probability for the next traces to "1,0"
    And I run "ThreeSpansScenario" and discard the initial p-value request
    And I wait to receive at least 1 traces
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 1"

  Scenario: Update P to 0 on first response: fail-permanent, fail-retriable, success
    Given I set the HTTP status code for the next requests to "200,400,500,200"
    Given I set the sampling probability for the next traces to "1,0"
    And I run "ThreeSpansScenario" and discard the initial p-value request
    And I wait to receive at least 1 traces
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 1"

  Scenario: Update P to 0 on first response: fail-permanent, success, fail-retriable
    Given I set the HTTP status code for the next requests to "200,400,200,500"
    Given I set the sampling probability for the next traces to "1,0"
    And I run "ThreeSpansScenario" and discard the initial p-value request
    And I wait to receive at least 1 traces
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 1"

  # P=0 on second response

  Scenario: Update P to 0 on second response: success, fail-permanent, fail-retriable
    Given I set the HTTP status code for the next requests to "200,200,400,500"
    Given I set the sampling probability for the next traces to "1,null,0"
    And I run "ThreeSpansScenario" and discard the initial p-value request
    And I wait to receive at least 2 traces
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 1"
    And I discard the oldest trace
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 2"

  Scenario: Update P to 0 on second response: success, fail-retriable, fail-permanent
    Given I set the HTTP status code for the next requests to "200,200,500,400"
    Given I set the sampling probability for the next traces to "1,null,0"
    And I run "ThreeSpansScenario" and discard the initial p-value request
    And I wait to receive at least 2 traces
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 1"
    And I discard the oldest trace
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 2"

  Scenario: Update P to 0 on second response: fail-retriable, fail-permanent, success
    Given I set the HTTP status code for the next requests to "200,500,400,200"
    Given I set the sampling probability for the next traces to "1,null,0"
    And I run "ThreeSpansScenario" and discard the initial p-value request
    And I wait to receive at least 2 traces
    # 500 - Payload rejected (retry)
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 1"
    And I discard the oldest trace
    # Retry of previously rejected trace
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 1"

  Scenario: Update P to 0 on second response: fail-retriable, success, fail-permanent
    Given I set the HTTP status code for the next requests to "200,500,200,400"
    Given I set the sampling probability for the next traces to "1,null,0"
    And I run "ThreeSpansScenario" and discard the initial p-value request
    And I wait to receive at least 2 traces
    # 500 - Payload rejected (retry)
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 1"
    And I discard the oldest trace
    # Retry of previously rejected trace
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 1"

  Scenario: Update P to 0 on second response: fail-permanent, fail-retriable, success
    Given I set the HTTP status code for the next requests to "200,400,500,200"
    Given I set the sampling probability for the next traces to "1,null,0"
    And I run "ThreeSpansScenario" and discard the initial p-value request
    And I wait to receive at least 2 traces
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 1"
    And I discard the oldest trace
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 2"

  Scenario: Update P to 0 on second response: fail-permanent, success, fail-retriable
    Given I set the HTTP status code for the next requests to "200,400,200,500"
    Given I set the sampling probability for the next traces to "1,null ,0"
    And I run "ThreeSpansScenario" and discard the initial p-value request
    And I wait to receive at least 2 traces
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 1"
    And I discard the oldest trace
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 2"
