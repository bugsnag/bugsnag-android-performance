Feature: Span Sampling

  Scenario: No spans should be sent when samplingProbability is zero
    Given I set the sampling probability for the next traces to "null,null,null"
    Given I run "SamplingProbabilityZeroScenario" and discard the initial p-value request
    Then I should receive no traces
