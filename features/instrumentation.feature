Feature: Automatic creation of spans

  @skip_above_android_9
  Scenario: Activity with full ViewLoad instrumentation
    Given I run "ActivityLoadInstrumentationScenario" configured as "FULL" and discard the initial p_value
    And I wait to receive 1 traces
    Then a span named "[ViewLoad/Activity]ActivityViewLoadActivity" contains the attributes:
                | attribute               | type        | value                    |
                | bugsnag.span.category   | stringValue | view_load                |
                | bugsnag.view.type       | stringValue | activity                 |
                | bugsnag.view.name       | stringValue | ActivityViewLoadActivity |

    * a span named "[ViewLoad/Fragment]LoaderFragment" contains the attributes:
                | attribute               | type        | value                    |
                | bugsnag.span.category   | stringValue | view_load                |
                | bugsnag.view.type       | stringValue | fragment                 |
                | bugsnag.view.name       | stringValue | LoaderFragment           |

  @skip_above_android_9
  Scenario: Activity with start-only ViewLoad instrumentation
    Given I run "ActivityLoadInstrumentationScenario" configured as "START_ONLY" and discard the initial p_value
    And I wait to receive 1 traces
    Then a span named "[ViewLoad/Activity]ActivityViewLoadActivity" contains the attributes:
                | attribute               | type        | value                    |
                | bugsnag.span.category   | stringValue | view_load                |
                | bugsnag.view.type       | stringValue | activity                 |
                | bugsnag.view.name       | stringValue | ActivityViewLoadActivity |

    * a span named "[ViewLoad/Fragment]LoaderFragment" contains the attributes:
               | attribute               | type        | value                    |
               | bugsnag.span.category   | stringValue | view_load                |
               | bugsnag.view.type       | stringValue | fragment                 |
               | bugsnag.view.name       | stringValue | LoaderFragment           |

  Scenario: Activity with no automatic ViewLoad instrumentation
    Given I run "ActivityLoadInstrumentationScenario" configured as "OFF" and discard the initial p_value
    And I wait to receive 1 traces
    Then a span named "[ViewLoad/Activity]ActivityViewLoadActivity" contains the attributes:
                | attribute               | type        | value                    |
                | bugsnag.span.category   | stringValue | view_load                |
                | bugsnag.view.type       | stringValue | activity                 |
                | bugsnag.view.name       | stringValue | ActivityViewLoadActivity |

  @skip_below_android_10
  Scenario: AppStart instrumentation
    Given I run "AppStartScenario"
    Then I relaunch the app after shutdown
    * I load scenario "AppStartScenario"
    And I wait for 5 spans
    * a span named "[AppStart/Cold]" contains the attributes:
                | attribute                         | type        | value          |
                | bugsnag.span.category             | stringValue | app_start      |
                | bugsnag.app_start.type            | stringValue | cold           |
                | bugsnag.app_start.first_view_name | stringValue | MainActivity   |

    * a span named "[ViewLoad/Activity]MainActivity" contains the attributes:
                | attribute               | type        | value                    |
                | bugsnag.span.category   | stringValue | view_load                |
                | bugsnag.view.type       | stringValue | activity                 |
                | bugsnag.view.name       | stringValue | MainActivity             |

    * a span named "[ViewLoadPhase/ActivityCreate]MainActivity" contains the attributes:
               | attribute               | type        | value                     |
               | bugsnag.span.category   | stringValue | view_load_phase           |
               | bugsnag.phase           | stringValue | ActivityCreate            |
               | bugsnag.view.name       | stringValue | MainActivity              |

    * a span named "[ViewLoadPhase/ActivityStart]MainActivity" contains the attributes:
               | attribute               | type        | value                     |
               | bugsnag.span.category   | stringValue | view_load_phase           |
               | bugsnag.phase           | stringValue | ActivityStart             |
               | bugsnag.view.name       | stringValue | MainActivity              |

    * a span named "[ViewLoadPhase/ActivityResume]MainActivity" contains the attributes:
               | attribute               | type        | value                     |
               | bugsnag.span.category   | stringValue | view_load_phase           |
               | bugsnag.phase           | stringValue | ActivityResume            |
               | bugsnag.view.name       | stringValue | MainActivity              |

  @skip_above_android_9
  Scenario: AppStart instrumentation
    Given I run "AppStartScenario"
    Then I relaunch the app after shutdown
    * I load scenario "AppStartScenario"
    And I wait for 2 spans
    * a span named "[AppStart/Cold]" contains the attributes:
                | attribute                         | type        | value          |
                | bugsnag.span.category             | stringValue | app_start      |
                | bugsnag.app_start.type            | stringValue | cold           |
                | bugsnag.app_start.first_view_name | stringValue | MainActivity   |

    * a span named "[ViewLoad/Activity]MainActivity" contains the attributes:
                | attribute               | type        | value                    |
                | bugsnag.span.category   | stringValue | view_load                |
                | bugsnag.view.type       | stringValue | activity                 |
                | bugsnag.view.name       | stringValue | MainActivity             |

  @skip_below_android_10
  Scenario: Activity load breakdown with full ViewLoad instrumentation
    Given I run "ActivityLoadInstrumentationScenario" configured as "FULL" and discard the initial p_value
    And I wait for 5 spans
    Then a span named "[ViewLoad/Activity]ActivityViewLoadActivity" contains the attributes:
                | attribute               | type        | value                    |
                | bugsnag.span.category   | stringValue | view_load                |
                | bugsnag.view.type       | stringValue | activity                 |
                | bugsnag.view.name       | stringValue | ActivityViewLoadActivity |

    * a span named "[ViewLoadPhase/ActivityCreate]ActivityViewLoadActivity" contains the attributes:
               | attribute               | type        | value                     |
               | bugsnag.span.category   | stringValue | view_load_phase           |
               | bugsnag.phase           | stringValue | ActivityCreate            |
               | bugsnag.view.name       | stringValue | ActivityViewLoadActivity  |

    * a span named "[ViewLoadPhase/ActivityStart]ActivityViewLoadActivity" contains the attributes:
               | attribute               | type        | value                     |
               | bugsnag.span.category   | stringValue | view_load_phase           |
               | bugsnag.phase           | stringValue | ActivityStart             |
               | bugsnag.view.name       | stringValue | ActivityViewLoadActivity  |

    * a span named "[ViewLoadPhase/ActivityResume]ActivityViewLoadActivity" contains the attributes:
               | attribute               | type        | value                     |
               | bugsnag.span.category   | stringValue | view_load_phase           |
               | bugsnag.phase           | stringValue | ActivityResume            |
               | bugsnag.view.name       | stringValue | ActivityViewLoadActivity  |

    * a span named "[ViewLoad/Fragment]LoaderFragment" contains the attributes:
               | attribute               | type        | value                     |
               | bugsnag.span.category   | stringValue | view_load                 |
               | bugsnag.view.type       | stringValue | fragment                  |
               | bugsnag.view.name       | stringValue | LoaderFragment            |

  @skip_below_android_10
  Scenario: Activity load breakdown with start-only ViewLoad instrumentation
    Given I run "ActivityLoadInstrumentationScenario" configured as "START_ONLY" and discard the initial p_value
    And I wait for 5 spans
    Then a span named "[ViewLoad/Activity]ActivityViewLoadActivity" contains the attributes:
                | attribute               | type        | value                    |
                | bugsnag.span.category   | stringValue | view_load                |
                | bugsnag.view.type       | stringValue | activity                 |
                | bugsnag.view.name       | stringValue | ActivityViewLoadActivity |

    * a span named "[ViewLoadPhase/ActivityCreate]ActivityViewLoadActivity" contains the attributes:
               | attribute               | type        | value                     |
               | bugsnag.span.category   | stringValue | view_load_phase           |
               | bugsnag.phase           | stringValue | ActivityCreate            |
               | bugsnag.view.name       | stringValue | ActivityViewLoadActivity  |

    * a span named "[ViewLoadPhase/ActivityStart]ActivityViewLoadActivity" contains the attributes:
               | attribute               | type        | value                     |
               | bugsnag.span.category   | stringValue | view_load_phase           |
               | bugsnag.phase           | stringValue | ActivityStart             |
               | bugsnag.view.name       | stringValue | ActivityViewLoadActivity  |

    * a span named "[ViewLoadPhase/ActivityResume]ActivityViewLoadActivity" contains the attributes:
               | attribute               | type        | value                     |
               | bugsnag.span.category   | stringValue | view_load_phase           |
               | bugsnag.phase           | stringValue | ActivityResume            |
               | bugsnag.view.name       | stringValue | ActivityViewLoadActivity  |

    * a span named "[ViewLoad/Fragment]LoaderFragment" contains the attributes:
               | attribute               | type        | value                     |
               | bugsnag.span.category   | stringValue | view_load                 |
               | bugsnag.view.type       | stringValue | fragment                  |
               | bugsnag.view.name       | stringValue | LoaderFragment            |
