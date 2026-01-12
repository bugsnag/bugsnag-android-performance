Feature: Loading indicator view keep view load span open

  Scenario: Loading indicator view
    Given I run "ExtendViewLoadSpanScenario"
    And I wait to receive a span named "[ViewLoad/Activity]LoadingIndicatorViewActivity"
    Then the "[ViewLoad/Activity]LoadingIndicatorViewActivity" span took at least 100 ms

  Scenario: Loading indicator view with name
    Given I run "NamedLoadingIndicatorScenario"
    And I wait to receive a span named "[ViewLoad/Activity]NamedLoadingIndicatorViewActivity"
    Then the "[ViewLoad/Activity]NamedLoadingIndicatorViewActivity" span took at least 100 ms
    And a span named "LoadingIndicatorSpan" has a parent named "[ViewLoad/Activity]NamedLoadingIndicatorViewActivity"