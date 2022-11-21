Feature: Span Sampling

  Scenario: No spans should be sent when samplingProbability is zero
    Given I run "SamplingProbabilityZeroScenario" and discard the initial p-value request
    Then I should receive no traces
