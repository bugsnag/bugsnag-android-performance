Feature: Automatic creation of spans

  Scenario: Activity with full ViewLoad instrumentation
    Given I run "ActivityLoadInstrumentationScenario" configured as "FULL" and discard the initial p-value request
    And I wait to receive 1 traces
    Then a span name equals "ViewLoad/Activity/ActivityViewLoadActivity"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0" string attribute "bugsnag.span.category" equals "view_load"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0" string attribute "bugsnag.view.type" equals "activity"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0" string attribute "bugsnag.view.name" equals "ActivityViewLoadActivity"

  Scenario: Activity with start-only ViewLoad instrumentation
    Given I run "ActivityLoadInstrumentationScenario" configured as "START_ONLY" and discard the initial p-value request
    And I wait to receive 1 traces
    Then a span name equals "ViewLoad/Activity/ActivityViewLoadActivity"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0" string attribute "bugsnag.span.category" equals "view_load"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0" string attribute "bugsnag.view.type" equals "activity"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0" string attribute "bugsnag.view.name" equals "ActivityViewLoadActivity"

  Scenario: Activity with no automatic ViewLoad instrumentation
    Given I run "ActivityLoadInstrumentationScenario" configured as "OFF" and discard the initial p-value request
    And I wait to receive 1 traces
    Then a span name equals "ViewLoad/Activity/ActivityViewLoadActivity"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0" string attribute "bugsnag.span.category" equals "view_load"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0" string attribute "bugsnag.view.type" equals "activity"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0" string attribute "bugsnag.view.name" equals "ActivityViewLoadActivity"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0" boolean attribute "manual_start" is true

  Scenario: AppStart instrumentation
    Given I run "AppStartScenario"
    Then I relaunch the app after shutdown
    * I load scenario "AppStartScenario"
    * I wait to receive 2 traces
    * I discard the oldest trace
    * a span name equals "AppStart/Cold"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0" string attribute "bugsnag.span.category" equals "app_start"
    * the trace payload field "resourceSpans.0.scopeSpans.0.spans.0" string attribute "bugsnag.app_start.type" equals "cold"
