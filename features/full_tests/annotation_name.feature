Feature: Bugsnag annotation for activity and fragment names

  Scenario: Activity with full ViewLoad instrumentation
    Given I run "AnnotatedActivityScenario"
    And I wait to receive a trace
    Then a span named "[ViewLoad/Activity]TestActivityName" contains the attributes:
      | attribute             | type        | value                    |
      | bugsnag.span.category | stringValue | view_load                |
      | bugsnag.view.type     | stringValue | activity                 |
      | bugsnag.view.name     | stringValue | TestActivityName         |

    * a span named "[ViewLoad/Fragment]TestFragmentName" contains the attributes:
      | attribute             | type        | value             |
      | bugsnag.span.category | stringValue | view_load         |
      | bugsnag.view.type     | stringValue | fragment          |
      | bugsnag.view.name     | stringValue | TestFragmentName  |
