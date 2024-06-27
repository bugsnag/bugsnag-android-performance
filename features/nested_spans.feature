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

    * a span named "[AppStart/AndroidCold]" contains the attributes:
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
                | bugsnag.span.first_class          | boolValue   | true                |

    * a span named "LoadData" contains the attributes:
                | attribute                         | type        | value               |
                | bugsnag.span.first_class          | boolValue   | true                |

    * a span named "CustomRoot" contains the attributes:
                | attribute                         | type        | value               |
                | bugsnag.span.first_class          | boolValue   | true                |

    # Check span parentage (nested under NestedSpansActivity)
    * the "[ViewLoadPhase/ActivityStart]NestedSpansActivity" span field "spanId" is stored as the value "activity_start_span_id"
    * the "[ViewLoadPhase/ActivityResume]NestedSpansActivity" span field "spanId" is stored as the value "activity_resume_span_id"
    * the "[ViewLoad/Activity]NestedSpansActivity" span field "spanId" is stored as the value "view_load_span_id"
    * the "CustomRoot" span field "spanId" is stored as the value "custom_root_span_id"

    # ViewLoadPhase phase spans (Create, Start, Resume) should be nested under ViewLoad
    * the "[ViewLoadPhase/ActivityCreate]NestedSpansActivity" span field "parentSpanId" equals the stored value "view_load_span_id"
    * the "[ViewLoadPhase/ActivityStart]NestedSpansActivity" span field "parentSpanId" equals the stored value "view_load_span_id"
    * the "[ViewLoadPhase/ActivityResume]NestedSpansActivity" span field "parentSpanId" equals the stored value "view_load_span_id"

    # FirstFragment should be nested under ViewLoadPhase/Start
    * the "[ViewLoad/Fragment]FirstFragment" span field "parentSpanId" equals the stored value "activity_start_span_id"

    # CustomRoot should be nested under ViewLoadPhase/Resume
    * the "CustomRoot" span field "parentSpanId" equals the stored value "activity_resume_span_id"

    # Remaining spans (SecondFragment, DoStuff, LoadData) should be nested under CustomRoot
    * the "[ViewLoad/Fragment]SecondFragment" span field "parentSpanId" equals the stored value "custom_root_span_id"
    * the "DoStuff" span field "parentSpanId" equals the stored value "custom_root_span_id"
    * the "LoadData" span field "parentSpanId" equals the stored value "custom_root_span_id"

