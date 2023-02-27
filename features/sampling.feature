Feature: Span Sampling

  Scenario: No spans should be sent when samplingProbability is zero
    Given I set the sampling probability for the next traces to "null,null,null"
    When I run "SamplingProbabilityZeroScenario" and discard the initial p-value request
    Then I should have received no spans
