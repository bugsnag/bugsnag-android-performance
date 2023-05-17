Feature: Configuration for discarding Spans

  Scenario: No spans should be sent when samplingProbability is zero
    Given I set the sampling probability for the next traces to "null,null,null"
    When I run "SamplingProbabilityZeroScenario"
    Then I should have received no spans

  Scenario: No spans should be sent when releaseStage is disabled
    When I run "DisabledReleaseStageScenario"
    Then I should have received no spans
