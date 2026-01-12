Feature: Rendering / Frame Metrics

Scenario: Slow & Frozen Frames are reported
  When I run "FrameMetricsScenario"
  * I wait to receive a span named "Slow Animation"
  * I wait to receive a span named "FrozenFrame"

  # The FrozenFrame span should be a child of Slow Animation
  * a span named "FrozenFrame" has a parent named "Slow Animation"

  * the "Slow Animation" span integer attribute "bugsnag.rendering.slow_frames" is greater than 0
  * the "Slow Animation" span integer attribute "bugsnag.rendering.frozen_frames" is greater than 0
  * the "Slow Animation" span integer attribute "bugsnag.rendering.total_frames" is greater than 0

Scenario: Rending Instrumentation can be turned off
  When I run "FrameMetricsScenario" configured as "disableInstrumentation"
  * I wait to receive a span named "Slow Animation"

  * the "Slow Animation" span has no "bugsnag.rendering.slow_frames" attribute
  * the "Slow Animation" span has no "bugsnag.rendering.frozen_frames" attribute
  * the "Slow Animation" span has no "bugsnag.rendering.total_frames" attribute
