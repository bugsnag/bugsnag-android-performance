Feature: Server responses

  Scenario: Startup P request returns 0
    Given I set the sampling probability for the next traces to "0"
    And I run "ThreeSpansScenario" and discard the initial p-value request
    And I should receive no traces

  Scenario: No P update: success, fail-permament, fail-retriable
    Given I set the HTTP status code for the next requests to "200,200,400,500"
    And I run "ThreeSpansScenario" and discard the initial p-value request
    And I wait to receive 3 traces
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 1"
    And I discard the oldest trace
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 2"
    And I discard the oldest trace
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 3"

  Scenario: No P update: success, fail-retriable, fail-permament
    Given I set the HTTP status code for the next requests to "200,200,500,400"
    And I run "ThreeSpansScenario" and discard the initial p-value request
    And I wait to receive 3 traces
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 1"
    And I discard the oldest trace
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 2"
    And I discard the oldest trace
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 2"
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.1.name" equals "Custom/span 3"

  Scenario: No P update: fail-retriable, fail-permament, success
    Given I set the HTTP status code for the next requests to "200,500,400,200"
    And I run "ThreeSpansScenario" and discard the initial p-value request
    And I wait to receive 3 traces
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 1"
    And I discard the oldest trace
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 1"
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.1.name" equals "Custom/span 2"
    And I discard the oldest trace
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 3"

  Scenario: No P update: fail-retriable, success, fail-permament
    Given I set the HTTP status code for the next requests to "200,500,200,400"
    And I run "ThreeSpansScenario" and discard the initial p-value request
    And I wait to receive 3 traces
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 1"
    And I discard the oldest trace
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 1"
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.1.name" equals "Custom/span 2"
    And I discard the oldest trace
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 3"

  Scenario: No P update: fail-permament, fail-retriable, success
    Given I set the HTTP status code for the next requests to "200,400,500,200"
    And I run "ThreeSpansScenario" and discard the initial p-value request
    And I wait to receive 3 traces
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 1"
    And I discard the oldest trace
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 2"
    And I discard the oldest trace
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 2"
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.1.name" equals "Custom/span 3"

  Scenario: No P update: fail-permament, success, fail-retriable
    Given I set the HTTP status code for the next requests to "200,400,200,500"
    And I run "ThreeSpansScenario" and discard the initial p-value request
    And I wait to receive 3 traces
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 1"
    And I discard the oldest trace
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 2"
    And I discard the oldest trace
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 3"

  # P=0 on first response

  Scenario: Update P to 0 on first response: success, fail-permament, fail-retriable
    Given I set the HTTP status code for the next requests to "200,200,400,500"
    Given I set the sampling probability for the next traces to "null,0"
    And I run "ThreeSpansScenario" and discard the initial p-value request
    And I wait to receive 1 traces
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 1"

  Scenario: Update P to 0 on first response: success, fail-retriable, fail-permament
    Given I set the HTTP status code for the next requests to "200,200,500,400"
    Given I set the sampling probability for the next traces to "null,0"
    And I run "ThreeSpansScenario" and discard the initial p-value request
    And I wait to receive 1 traces
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 1"

  Scenario: Update P to 0 on first response: fail-retriable, fail-permament, success
    Given I set the HTTP status code for the next requests to "200,500,400,200"
    Given I set the sampling probability for the next traces to "null,0"
    And I run "ThreeSpansScenario" and discard the initial p-value request
    And I wait to receive 1 traces
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 1"

  Scenario: Update P to 0 on first response: fail-retriable, success, fail-permament
    Given I set the HTTP status code for the next requests to "200,500,200,400"
    Given I set the sampling probability for the next traces to "null,0"
    And I run "ThreeSpansScenario" and discard the initial p-value request
    And I wait to receive 1 traces
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 1"

  Scenario: Update P to 0 on first response: fail-permament, fail-retriable, success
    Given I set the HTTP status code for the next requests to "200,400,500,200"
    Given I set the sampling probability for the next traces to "null,0"
    And I run "ThreeSpansScenario" and discard the initial p-value request
    And I wait to receive 1 traces
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 1"

  Scenario: Update P to 0 on first response: fail-permament, success, fail-retriable
    Given I set the HTTP status code for the next requests to "200,400,200,500"
    Given I set the sampling probability for the next traces to "null,0"
    And I run "ThreeSpansScenario" and discard the initial p-value request
    And I wait to receive 1 traces
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 1"

  # P=0 on second response

  Scenario: Update P to 0 on second response: success, fail-permament, fail-retriable
    Given I set the HTTP status code for the next requests to "200,200,400,500"
    Given I set the sampling probability for the next traces to "null,null,0"
    And I run "ThreeSpansScenario" and discard the initial p-value request
    And I wait to receive 2 traces
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 1"
    And I discard the oldest trace
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 2"

  Scenario: Update P to 0 on second response: success, fail-retriable, fail-permament
    Given I set the HTTP status code for the next requests to "200,200,500,400"
    Given I set the sampling probability for the next traces to "null,null,0"
    And I run "ThreeSpansScenario" and discard the initial p-value request
    And I wait to receive 2 traces
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 1"
    And I discard the oldest trace
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 2"

  Scenario: Update P to 0 on second response: fail-retriable, fail-permament, success
    Given I set the HTTP status code for the next requests to "200,500,400,200"
    Given I set the sampling probability for the next traces to "null,null,0"
    And I run "ThreeSpansScenario" and discard the initial p-value request
    And I wait to receive 2 traces
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 1"
    And I discard the oldest trace
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 1"
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.1.name" equals "Custom/span 2"

  Scenario: Update P to 0 on second response: fail-retriable, success, fail-permament
    Given I set the HTTP status code for the next requests to "200,500,200,400"
    Given I set the sampling probability for the next traces to "null,null,0"
    And I run "ThreeSpansScenario" and discard the initial p-value request
    And I wait to receive 2 traces
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 1"
    And I discard the oldest trace
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 1"
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.1.name" equals "Custom/span 2"

  Scenario: Update P to 0 on second response: fail-permament, fail-retriable, success
    Given I set the HTTP status code for the next requests to "200,400,500,200"
    Given I set the sampling probability for the next traces to "null,null,0"
    And I run "ThreeSpansScenario" and discard the initial p-value request
    And I wait to receive 2 traces
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 1"
    And I discard the oldest trace
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 2"

  Scenario: Update P to 0 on second response: fail-permament, success, fail-retriable
    Given I set the HTTP status code for the next requests to "200,400,200,500"
    Given I set the sampling probability for the next traces to "null,null,0"
    And I run "ThreeSpansScenario" and discard the initial p-value request
    And I wait to receive 2 traces
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 1"
    And I discard the oldest trace
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "Custom/span 2"
