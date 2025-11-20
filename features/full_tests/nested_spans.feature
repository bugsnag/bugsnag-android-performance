Feature: Nested spans

  @skip_below_android_10
  Scenario: Nested spans
    Given I run "NestedSpansScenario"
    And I wait to receive a trace
    # Check we have received all the spans we are expecting
    * a span named "[ViewLoadPhase/ActivityCreate]NestedSpansActivity" contains the attributes:
                | attribute                         | type        | value               |
                | bugsnag.span.category             | stringValue | view_load_phase     |
                | bugsnag.phase                     | stringValue | ActivityCreate      |
                | bugsnag.view.name                 | stringValue | NestedSpansActivity |

    * a span named "[ViewLoadPhase/ActivityStart]NestedSpansActivity" contains the attributes:
                | attribute                         | type        | value               |
                | bugsnag.span.category             | stringValue | view_load_phase     |
                | bugsnag.phase                     | stringValue | ActivityStart       |
                | bugsnag.view.name                 | stringValue | NestedSpansActivity |

    * a span named "[ViewLoad/Fragment]FirstFragment" contains the attributes:
                | attribute                         | type        | value               |
                | bugsnag.span.category             | stringValue | view_load           |
                | bugsnag.view.type                 | stringValue | fragment            |
                | bugsnag.view.name                 | stringValue | FirstFragment       |

    * a span named "[ViewLoadPhase/FragmentCreate]FirstFragment" contains the attributes:
                | attribute                         | type        | value               |
                | bugsnag.span.category             | stringValue | view_load_phase     |
                | bugsnag.view.type                 | stringValue | fragment            |
                | bugsnag.view.name                 | stringValue | FirstFragment       |
                | bugsnag.phase                     | stringValue | FragmentCreate      |

    * a span named "[ViewLoad/Fragment]SecondFragment" contains the attributes:
                | attribute                         | type        | value               |
                | bugsnag.span.category             | stringValue | view_load           |
                | bugsnag.view.type                 | stringValue | fragment            |
                | bugsnag.view.name                 | stringValue | SecondFragment      |

    * a span named "[ViewLoadPhase/FragmentCreate]SecondFragment" contains the attributes:
                | attribute                         | type        | value               |
                | bugsnag.span.category             | stringValue | view_load_phase     |
                | bugsnag.view.type                 | stringValue | fragment            |
                | bugsnag.view.name                 | stringValue | SecondFragment      |
                | bugsnag.phase                     | stringValue | FragmentCreate      |

    * a span named "[ViewLoadPhase/ActivityResume]NestedSpansActivity" contains the attributes:
                | attribute                         | type        | value               |
                | bugsnag.span.category             | stringValue | view_load_phase     |
                | bugsnag.phase                     | stringValue | ActivityResume      |
                | bugsnag.view.name                 | stringValue | NestedSpansActivity |

    * a span named "[AppStart/AndroidCold]SplashScreen" contains the attributes:
                | attribute                         | type        | value               |
                | bugsnag.span.category             | stringValue | app_start           |
                | bugsnag.app_start.type            | stringValue | cold                |

    * a span named "[ViewLoad/Activity]NestedSpansActivity" contains the attributes:
                | attribute                         | type        | value               |
                | bugsnag.span.category             | stringValue | view_load           |
                | bugsnag.view.type                 | stringValue | activity            |
                | bugsnag.view.name                 | stringValue | NestedSpansActivity |
                | bugsnag.span.first_class          | boolValue   | true                |

    * a span named "DoStuff" contains the attributes:
                | attribute                         | type        | value               |
                | bugsnag.span.category             | stringValue | custom              |
                | bugsnag.span.first_class          | boolValue   | true                |

    * a span named "LoadData" contains the attributes:
                | attribute                         | type        | value               |
                | bugsnag.span.category             | stringValue | custom              |
                | bugsnag.span.first_class          | boolValue   | true                |

    * a span named "CustomRoot" contains the attributes:
                | attribute                         | type        | value               |
                | bugsnag.span.category             | stringValue | custom              |
                | bugsnag.span.first_class          | boolValue   | true                |

    # ViewLoadPhase phase spans (Create, Start, Resume) should be nested under ViewLoad
    * a span named "[ViewLoadPhase/ActivityCreate]NestedSpansActivity" has a parent named "[ViewLoad/Activity]NestedSpansActivity"
    * a span named "[ViewLoadPhase/ActivityStart]NestedSpansActivity" has a parent named "[ViewLoad/Activity]NestedSpansActivity"
    * a span named "[ViewLoadPhase/ActivityResume]NestedSpansActivity" has a parent named "[ViewLoad/Activity]NestedSpansActivity"

    # FirstFragment should be nested under ViewLoadPhase/Start
    * a span named "[ViewLoad/Fragment]FirstFragment" has a parent named "[ViewLoadPhase/ActivityStart]NestedSpansActivity"

    # CustomRoot should be nested under ViewLoadPhase/Resume
    * a span named "CustomRoot" has a parent named "[ViewLoadPhase/ActivityResume]NestedSpansActivity"

    # Remaining spans (SecondFragment, DoStuff, LoadData) should be nested under CustomRoot
    * a span named "[ViewLoad/Fragment]SecondFragment" has a parent named "CustomRoot"
    * a span named "DoStuff" has a parent named "CustomRoot"
    * a span named "LoadData" has a parent named "CustomRoot"

