Feature: Rendering / Frame Metrics

Scenario: Slow & Frozen Frames are reported
  When I run "FrameMetricsScenario"
  * I wait to receive a trace

  * a span name equals "Slow Animation"
  * a span name equals "FrozenFrame"

  # The FrozenFrame span should be a child of Slow Animation
  * the "Slow Animation" span field "spanId" is stored as the value "slow_animation_spanId"
  * the "FrozenFrame" span field "parentSpanId" equals the stored value "slow_animation_spanId"

  * the "Slow Animation" span integer attribute "bugsnag.rendering.slow_frames" is greater than 0
  * the "Slow Animation" span integer attribute "bugsnag.rendering.frozen_frames" is greater than 0
  * the "Slow Animation" span integer attribute "bugsnag.rendering.total_frames" is greater than 0

  * the "Slow Animation" span has array attribute named "bugsnag.system.cpu_measures_total"
  * the "Slow Animation" span has array attribute named "bugsnag.system.cpu_measures_main_thread"
  * the "Slow Animation" span has array attribute named "bugsnag.system.cpu_measures_timestamps"

  * the "Slow Animation" span has array attribute named "bugsnag.system.memory.spaces.device.used"
  * the "Slow Animation" span has array attribute named "bugsnag.system.memory.spaces.art.used"
  * the "Slow Animation" span has array attribute named "bugsnag.system.memory.timestamps"

Scenario: Rending Instrumentation can be turned off
  When I run "FrameMetricsScenario" configured as "disableInstrumentation"
  * I wait to receive a trace

  * a span name equals "Slow Animation"

  * the "Slow Animation" span has no "bugsnag.rendering.slow_frames" attribute
  * the "Slow Animation" span has no "bugsnag.rendering.frozen_frames" attribute
  * the "Slow Animation" span has no "bugsnag.rendering.total_frames" attribute
