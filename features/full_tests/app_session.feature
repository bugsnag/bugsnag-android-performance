Feature: App Session Metrics

  Scenario: CPU and memory aggregates satisfy min <= mean <= max
    When I run "AppSessionScenario" configured as "all_enabled"
    And I wait to receive a span named "app_session"
    Then the "app_session" span metrics "bugsnag.session.cpu" satisfy min <= mean <= max
    And the "app_session" span metrics "bugsnag.session.memory.runtime" satisfy min <= mean <= max
    And the "app_session" span metrics "bugsnag.session.memory.device" satisfy min <= mean <= max

  Scenario: Single sample produces min equals max equals mean
    When I run "AppSessionScenario" configured as "single_sample"
    And I wait to receive a span named "app_session"
    Then the "app_session" span metrics "bugsnag.session.cpu" are equal
    And the "app_session" span metrics "bugsnag.session.memory.runtime" are equal

  Scenario: CPU disabled but memory enabled
    When I run "AppSessionScenario" configured as "cpu_disabled"
    And I wait to receive a span named "app_session"
    Then the "app_session" span has no "bugsnag.session.cpu.min" attribute
    And the "app_session" span has int attribute named "bugsnag.session.memory.runtime.min"

  Scenario: Memory disabled but CPU enabled
    When I run "AppSessionScenario" configured as "memory_disabled"
    And I wait to receive a span named "app_session"
    Then the "app_session" span has double attribute named "bugsnag.session.cpu.min"
    And the "app_session" span has no "bugsnag.session.memory.runtime.min" attribute

  Scenario: App session span contains all CPU sub-metrics
    When I run "AppSessionScenario" configured as "all_enabled"
    And I wait to receive a span named "app_session"
    Then the "app_session" span has double attribute named "bugsnag.system.cpu_mean_total"
    And the "app_session" span has double attribute named "bugsnag.system.cpu_mean_main_thread"
    And the "app_session" span has double attribute named "bugsnag.system.cpu_mean_overhead"

  Scenario: Two concurrent app sessions deliver independently
    When I run "AppSessionScenario" configured as "concurrent"
    And I wait to receive a span named "app_session"
    And I wait to receive a span named "app_session"

  Scenario: Force-terminated session is lost
    When I run "AppSessionScenario" configured as "force_terminate"
    And I relaunch the app after shutdown
    Then I received no span named "app_session"

  Scenario: Switch off or restart due to version upgrade
    When I run "AppSessionScenario" configured as "switch_off"
    And I relaunch the app after shutdown
    Then I received no span named "app_session"
