Feature: Automatic creation of spans

  Scenario: Activity with full ViewLoad instrumentation
    Given I run "ActivityLoadInstrumentationScenario" configured as "FULL"
    And I wait to receive 1 traces
    Then a span name equals "ViewLoad/Activity/ActivityViewLoadActivity"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0" attribute "bugsnag.span_category" equals "view_load"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0" attribute "bugsnag.view.type" equals "activity"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0" attribute "bugsnag.view.name" equals "ActivityViewLoadActivity"

  Scenario: Activity with start-only ViewLoad instrumentation
    Given I run "ActivityLoadInstrumentationScenario" configured as "START_ONLY"
    And I wait to receive 1 traces
    Then a span name equals "ViewLoad/Activity/ActivityViewLoadActivity"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0" attribute "bugsnag.span_category" equals "view_load"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0" attribute "bugsnag.view.type" equals "activity"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0" attribute "bugsnag.view.name" equals "ActivityViewLoadActivity"

  Scenario: Activity with no automatic ViewLoad instrumentation
    Given I run "ActivityLoadInstrumentationScenario" configured as "OFF"
    And I wait to receive 1 traces
    Then a span name equals "ViewLoad/Activity/ActivityViewLoadActivity"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0" attribute "bugsnag.span_category" equals "view_load"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0" attribute "bugsnag.view.type" equals "activity"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0" attribute "bugsnag.view.name" equals "ActivityViewLoadActivity"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0" attribute "manual_start" is true
