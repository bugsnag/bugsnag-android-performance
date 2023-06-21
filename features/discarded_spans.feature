Feature: Configuration for discarding Spans

  Scenario: No spans should be sent when releaseStage is disabled
    When I run "DisabledReleaseStageScenario"
    Then I should have received no spans
