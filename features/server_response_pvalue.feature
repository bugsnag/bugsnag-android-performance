Feature: Server responses P value
    # P=0 on first response

  Scenario: Update P to 0 on first response: success, fail-permanent, fail-retriable
#    Given I set the HTTP status code for the next requests to 200,200,400,500
    Given I set the HTTP status code for the next requests to 200,200
    And I load scenario "GenerateSpansScenario"
    And I wait to receive a sampling request
    Then I invoke "sendNextSpan"
    And I wait to receive 1 trace
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "span 1"
    Then I discard the oldest trace
    Given I set the HTTP status code for the next request to 400
    Then I invoke "sendNextSpan"
    And I wait to receive 1 trace
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "span 2"
    And I discard the oldest trace
    Given I set the HTTP status code for the next request to 500
    Then I invoke "sendNextSpan"
    And I wait to receive 2 traces

    * a span named "span 3" contains the attributes:
      | attribute                | type      | value |
      | bugsnag.span.first_class | boolValue | true  |

    * a span named "span 3" contains the attributes:
      | attribute                | type      | value |
      | bugsnag.span.first_class | boolValue | true  |

    Given I set the HTTP status code for the next request to 200
    Then I invoke "sendNextSpan"
    And I wait to receive 3 traces

    * a span named "span 3" contains the attributes:
      | attribute                | type      | value |
      | bugsnag.span.first_class | boolValue | true  |

    * a span named "span 3" contains the attributes:
      | attribute                | type      | value |
      | bugsnag.span.first_class | boolValue | true  |

    * a span named "span 4" contains the attributes:
      | attribute                | type      | value |
      | bugsnag.span.first_class | boolValue | true  |

  Scenario: Update P to 0 on first response: success, fail-retriable, fail-permanent
#    Given I set the HTTP status code for the next requests to 200,200,500,400
    Given I set the HTTP status code for the next requests to 200,200
    And I load scenario "GenerateSpansScenario"
    And I wait to receive a sampling request
    Then I invoke "sendNextSpan"
    And I wait to receive 1 trace
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "span 1"
    Then I discard the oldest trace
    Given I set the HTTP status code for the next request to 500
    Then I invoke "sendNextSpan"
    And I wait to receive 2 traces

    * a span named "span 2" contains the attributes:
      | attribute                | type      | value |
      | bugsnag.span.first_class | boolValue | true  |

    * a span named "span 2" contains the attributes:
      | attribute                | type      | value |
      | bugsnag.span.first_class | boolValue | true  |

    Then I discard the oldest trace
    Then I discard the oldest trace
    Given I set the HTTP status code for the next request to 400
    Then I invoke "sendNextSpan"
    And I wait to receive 1 trace
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "span 3"

  @skip
  Scenario: Update P to 0 on first response: fail-retriable, success, fail-permanent
#    Given I set the HTTP status code for the next requests to 200,500,200,400
    Given I set the HTTP status code for the next requests to 200,500
    And I load scenario "GenerateSpansScenario"
    And I wait to receive a sampling request
    Then I invoke "sendNextSpan"
    And I wait to receive at least 1 trace
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "span 1"
    Then I discard the oldest trace
    Given I set the HTTP status code for the next request to 200
    Then I invoke "sendNextSpan"
    And I wait to receive 2 traces

    * a span named "span 1" contains the attributes:
      | attribute                | type      | value |
      | bugsnag.span.first_class | boolValue | true  |

    * a span named "span 2" contains the attributes:
      | attribute                | type      | value |
      | bugsnag.span.first_class | boolValue | true  |

    Then I discard the oldest trace
    Then I discard the oldest trace
    Given I set the HTTP status code for the next request to 400
    Then I invoke "sendNextSpan"
    And I wait to receive 1 trace
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "span 3"
    Then I discard the oldest trace
    Given I set the HTTP status code for the next request to 200
    Then I invoke "sendNextSpan"
    And I wait to receive 1 trace
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "span 4"

  Scenario: Update P to 0 on first response: fail-permanent, fail-retriable, success
#    Given I set the HTTP status code for the next requests to 200,400,500,200
    Given I set the HTTP status code for the next requests to 200,400
    And I load scenario "GenerateSpansScenario"
    And I wait to receive a sampling request
    Then I invoke "sendNextSpan"
    And I wait to receive 1 trace
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "span 1"
    Then I discard the oldest trace
    Given I set the HTTP status code for the next request to 500
    Then I invoke "sendNextSpan"
    And I wait to receive 2 traces

    * a span named "span 2" contains the attributes:
      | attribute                | type      | value |
      | bugsnag.span.first_class | boolValue | true  |

    * a span named "span 2" contains the attributes:
      | attribute                | type      | value |
      | bugsnag.span.first_class | boolValue | true  |

    Then I discard the oldest trace
    Then I discard the oldest trace
    Given I set the HTTP status code for the next request to 200
    Then I invoke "sendNextSpan"
    And I wait to receive 1 trace
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "span 3"

  Scenario: Update P to 0 on first response: fail-permanent, success, fail-retriable
#    Given I set the HTTP status code for the next requests to 200,400,200,500
    Given I set the HTTP status code for the next requests to 200,400
    And I load scenario "GenerateSpansScenario"
    And I wait to receive a sampling request
    Then I invoke "sendNextSpan"
    And I wait to receive 1 trace
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "span 1"
    Then I discard the oldest trace
    Given I set the HTTP status code for the next request to 200
    Then I invoke "sendNextSpan"
    And I wait to receive 1 trace
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "span 2"
    Then I discard the oldest trace
    Given I set the HTTP status code for the next request to 500
    Then I invoke "sendNextSpan"
    And I wait to receive 2 traces

    * a span named "span 3" contains the attributes:
      | attribute                | type      | value |
      | bugsnag.span.first_class | boolValue | true  |

    * a span named "span 3" contains the attributes:
      | attribute                | type      | value |
      | bugsnag.span.first_class | boolValue | true  |

    Then I discard the oldest trace
    Then I discard the oldest trace
    Given I set the HTTP status code for the next request to 200
    Then I invoke "sendNextSpan"
    And I wait to receive 1 trace
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "span 4"

  # P=0 on second response

  Scenario: Update P to 0 on second response: success, fail-permanent, fail-retriable
    Given I set the HTTP status code for the next requests to 200,200
    And I set the sampling probability for the next traces to "1,null"
    And I load scenario "GenerateSpansScenario"
    Then I invoke "sendNextSpan"
    And I wait to receive 1 trace
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "span 1"
    And I discard the oldest trace
    Given I set the HTTP status code for the next request to 400
    And I set the sampling probability for the next traces to "0"
    And I invoke "sendNextSpan"
    And I wait to receive 1 trace
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "span 2"
    And I discard the oldest trace

    Given I set the HTTP status code for the next request to 400
    And I set the sampling probability for the next traces to "0"
    And I invoke "sendNextSpan"
    And I should receive no traces

  @skip
  Scenario: Update P to 0 on second response: fail-retriable, fail-permanent, success
#    Given I set the HTTP status code for the next requests to 200,500,400,200
    Given I set the HTTP status code for the next requests to 200,500
    And I load scenario "GenerateSpansScenario"
    And I wait to receive a sampling request
    Then I invoke "sendNextSpan"
    And I wait to receive at least 1 trace
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "span 1"
    Then I discard the oldest trace
    Given I set the HTTP status code for the next request to 400
    Then I invoke "sendNextSpan"
    And I wait to receive 2 traces

    * a span named "span 2" contains the attributes:
      | attribute                | type      | value |
      | bugsnag.span.first_class | boolValue | true  |

    * a span named "span 2" contains the attributes:
      | attribute                | type      | value |
      | bugsnag.span.first_class | boolValue | true  |

    Then I discard the oldest trace
    Then I discard the oldest trace
    Given I set the HTTP status code for the next request to 200
    Then I invoke "sendNextSpan"
    And I wait to receive 1 trace
    Then the trace payload field "resourceSpans.0.scopeSpans.0.spans.0.name" equals "span 3"