Feature: Automatic creation of spans

  Scenario: Activity with full ViewLoad instrumentation
    Given I run "ActivityLoadInstrumentationScenario" configured as "FULL" and discard the initial p-value request
    And I wait to receive 1 traces
    Then a span name equals "ViewLoad/Activity/ActivityViewLoadActivity"
    * a span string attribute "bugsnag.span.category" equals "view_load"
    * a span string attribute "bugsnag.view.type" equals "activity"
    * a span string attribute "bugsnag.view.name" equals "ActivityViewLoadActivity"

    * a span name equals "ViewLoad/Fragment/LoaderFragment"
    * a span string attribute "bugsnag.span.category" equals "view_load"
    * a span string attribute "bugsnag.view.type" equals "fragment"
    * a span string attribute "bugsnag.view.name" equals "LoaderFragment"

  Scenario: Activity with start-only ViewLoad instrumentation
    Given I run "ActivityLoadInstrumentationScenario" configured as "START_ONLY" and discard the initial p-value request
    And I wait to receive 1 traces
    Then a span name equals "ViewLoad/Activity/ActivityViewLoadActivity"
    * a span string attribute "bugsnag.span.category" equals "view_load"
    * a span string attribute "bugsnag.view.type" equals "activity"
    * a span string attribute "bugsnag.view.name" equals "ActivityViewLoadActivity"

    * a span name equals "ViewLoad/Fragment/LoaderFragment"
    * a span string attribute "bugsnag.span.category" equals "view_load"
    * a span string attribute "bugsnag.view.type" equals "fragment"
    * a span string attribute "bugsnag.view.name" equals "LoaderFragment"

  Scenario: Activity with no automatic ViewLoad instrumentation
    Given I run "ActivityLoadInstrumentationScenario" configured as "OFF" and discard the initial p-value request
    And I wait to receive 1 traces
    Then a span name equals "ViewLoad/Activity/ActivityViewLoadActivity"
    * a span string attribute "bugsnag.span.category" equals "view_load"
    * a span string attribute "bugsnag.view.type" equals "activity"
    * a span string attribute "bugsnag.view.name" equals "ActivityViewLoadActivity"

  Scenario: AppStart instrumentation
    Given I run "AppStartScenario"
    Then I relaunch the app after shutdown
    * I load scenario "AppStartScenario"
    * I wait to receive 2 traces
    * I discard the oldest trace
    * a span name equals "AppStart/Cold"
    * a span string attribute "bugsnag.span.category" equals "app_start"
    * a span string attribute "bugsnag.app_start.type" equals "cold"
