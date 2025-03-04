Feature: Loading indicator view keep view load span open

  Scenario: Loading indicator view
    Given I run "ExtendViewLoadSpanScenario"
    And I wait to receive a trace
    Then the "[ViewLoad/Activity]LoadingIndicatorViewActivity" span took at least 100 ms
