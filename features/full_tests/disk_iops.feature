Feature: Disk IOPS

# ROAD 2233 – Scenario 1
  Scenario Outline: SDK emits all 3 disk IOPS attributes as IntValue on eligible spans
    When I run disk IOPS Scenario 1 as "<span_type>"
    And I wait to receive a span named "<span_name>"
    Then the "<span_name>" span has integer attribute named "bugsnag.device.disk.iops_read"
    And the "<span_name>" span has integer attribute named "bugsnag.device.disk.iops_write"
    And the "<span_name>" span has integer attribute named "bugsnag.device.disk.iops_total"
    And the "<span_name>" span integer attribute "bugsnag.device.disk.iops_total" equals the sum of "bugsnag.device.disk.iops_read" and "bugsnag.device.disk.iops_write"

    Examples:
      | platform | span_type   | span_name                          |
      | android  | custom      | DiskIopsCustom                     |
      | android  | app_session | [AppSession/DiskIops]              |

    @skip_below_android_10
    Examples: cold AppStart
      | platform | span_type | span_name                          |
      | android  | app_start | [AppStart/AndroidCold]SplashScreen |

# ROAD 2233 – Scenario 2
  Scenario Outline: SDK reports real disk IOPS values computed on the device
    When I run "DiskIopsScenario" configured as "<span_type>"
    And I wait to receive a span named "<span_name>"
    Then the "<span_name>" span integer attribute "bugsnag.device.disk.iops_read" is greater than or equal to 0
    And the "<span_name>" span integer attribute "bugsnag.device.disk.iops_write" is greater than or equal to 0
    And the "<span_name>" span integer attribute "bugsnag.device.disk.iops_total" is greater than or equal to 0
    And the "<span_name>" span integer attribute "bugsnag.device.disk.iops_total" equals the sum of "bugsnag.device.disk.iops_read" and "bugsnag.device.disk.iops_write"
    And the "<span_name>" span integer attribute "bugsnag.internal.disk_io.read_start" is less than or equal to span integer attribute "bugsnag.internal.disk_io.read_end"
    And the "<span_name>" span integer attribute "bugsnag.internal.disk_io.write_start" is less than or equal to span integer attribute "bugsnag.internal.disk_io.write_end"

    Examples:
      | platform | span_type   | span_name             |
      | android  | custom      | DiskIopsCustom        |
      | android  | app_session | [AppSession/DiskIops] |

# Scenario 7b (sequential spans – fresh snapshots, no stale start counters)
  Scenario: Multiple sequential spans capture independent disk metrics
    When I run "DiskIopsSequentialScenario"
    And I wait to receive a span named "DiskIopsSequential1"
    And I wait to receive a span named "DiskIopsSequential2"
    Then the "DiskIopsSequential1" span has integer attribute named "bugsnag.device.disk.iops_read"
    And the "DiskIopsSequential1" span has integer attribute named "bugsnag.device.disk.iops_write"
    And the "DiskIopsSequential1" span has integer attribute named "bugsnag.device.disk.iops_total"
    And the "DiskIopsSequential2" span has integer attribute named "bugsnag.device.disk.iops_read"
    And the "DiskIopsSequential2" span has integer attribute named "bugsnag.device.disk.iops_write"
    And the "DiskIopsSequential2" span has integer attribute named "bugsnag.device.disk.iops_total"
    And the "DiskIopsSequential2" span integer attribute "bugsnag.internal.disk_io.read_start" is greater than or equal to the "DiskIopsSequential1" span integer attribute "bugsnag.internal.disk_io.read_end"
    And the "DiskIopsSequential2" span integer attribute "bugsnag.internal.disk_io.write_start" is greater than or equal to the "DiskIopsSequential1" span integer attribute "bugsnag.internal.disk_io.write_end"

# ROAD 2233 – Scenario 9 (disk IOPS across app lifecycle transitions)
  Scenario Outline: SDK captures disk IOPS across mid-span background/foreground
    Given I load scenario "DiskIopsLifecycleScenario"
    And I configure scenario "span_type" to "<span_type>"
    And I configure scenario "transition" to "mid_span_bg_fg"
    And I run the loaded scenario
    And I wait to receive a span named "<span_name>"
    Then the "<span_name>" span has integer attribute named "bugsnag.device.disk.iops_read"
    And the "<span_name>" span has integer attribute named "bugsnag.device.disk.iops_write"
    And the "<span_name>" span has integer attribute named "bugsnag.device.disk.iops_total"
    And the "<span_name>" span integer attribute "bugsnag.device.disk.iops_read" is greater than or equal to 0
    And the "<span_name>" span integer attribute "bugsnag.device.disk.iops_write" is greater than or equal to 0
    And the "<span_name>" span integer attribute "bugsnag.device.disk.iops_total" is greater than or equal to 0
    And the "<span_name>" span integer attribute "bugsnag.device.disk.iops_total" equals the sum of "bugsnag.device.disk.iops_read" and "bugsnag.device.disk.iops_write"

    Examples:
      | platform | span_type   | span_name             |
      | android  | custom      | DiskIopsCustom        |
      | android  | app_session | [AppSession/DiskIops] |

  Scenario Outline: SDK captures disk IOPS when span ends or starts in background
    Given I load scenario "DiskIopsLifecycleScenario"
    And I configure scenario "span_type" to "<span_type>"
    And I configure scenario "transition" to "<transition>"
    And I run the loaded scenario
    And I wait to receive a span named "<span_name>"
    Then the "<span_name>" span has integer attribute named "bugsnag.device.disk.iops_read"
    And the "<span_name>" span has integer attribute named "bugsnag.device.disk.iops_write"
    And the "<span_name>" span has integer attribute named "bugsnag.device.disk.iops_total"
    And the "<span_name>" span integer attribute "bugsnag.device.disk.iops_read" is greater than or equal to 0
    And the "<span_name>" span integer attribute "bugsnag.device.disk.iops_write" is greater than or equal to 0
    And the "<span_name>" span integer attribute "bugsnag.device.disk.iops_total" is greater than or equal to 0
    And the "<span_name>" span integer attribute "bugsnag.device.disk.iops_total" equals the sum of "bugsnag.device.disk.iops_read" and "bugsnag.device.disk.iops_write"

    Examples:
      | platform | span_type   | transition           | span_name             |
      | android  | custom      | ends_in_background   | DiskIopsCustom        |
      | android  | custom      | starts_in_background | DiskIopsCustom        |
      | android  | app_session | ends_in_background   | [AppSession/DiskIops] |
      | android  | app_session | starts_in_background | [AppSession/DiskIops] |

# ROAD 2233 – Scenario 10 (spans from older SDK without disk IOPS)
  Scenario: SDK omits disk IOPS when disk metrics are disabled
    When I run "DiskIopsDisabledScenario"
    And I wait to receive a span named "DiskIopsDisabled"
    Then the "DiskIopsDisabled" span has no "bugsnag.device.disk.iops_read" attribute
    And the "DiskIopsDisabled" span has no "bugsnag.device.disk.iops_write" attribute
    And the "DiskIopsDisabled" span has no "bugsnag.device.disk.iops_total" attribute

# ROAD 2233 – Scenario 11 (high and burst I/O – valid Int64)
  Scenario Outline: SDK reports valid disk IOPS under high and burst I/O
    When I run "DiskIopsWorkloadScenario" configured as "<workload>"
    And I wait to receive a span named "DiskIopsWorkload"
    Then the "DiskIopsWorkload" span has integer attribute named "bugsnag.device.disk.iops_read"
    And the "DiskIopsWorkload" span has integer attribute named "bugsnag.device.disk.iops_write"
    And the "DiskIopsWorkload" span has integer attribute named "bugsnag.device.disk.iops_total"
    And the "DiskIopsWorkload" span integer attribute "bugsnag.device.disk.iops_read" is greater than or equal to 0
    And the "DiskIopsWorkload" span integer attribute "bugsnag.device.disk.iops_write" is greater than or equal to 0
    And the "DiskIopsWorkload" span integer attribute "bugsnag.device.disk.iops_total" is greater than 0
    And the "DiskIopsWorkload" span integer attribute "bugsnag.device.disk.iops_total" equals the sum of "bugsnag.device.disk.iops_read" and "bugsnag.device.disk.iops_write"
    And the "DiskIopsWorkload" span attribute "bugsnag.device.disk.iops_total" is encoded as intValue

    Examples:
      | platform | workload    |
      | android  | sqlite      |
      | android  | burst_write |
      | android  | file_copy   |

# Scenario 12 (OTLP payload structure for disk IOPS)
  Scenario: OTLP payload contains exactly 3 disk IOPS attributes with intValue encoding
    When I run "DiskIopsScenario" configured as "custom"
    And I wait to receive a span named "DiskIopsCustom"
    Then the "DiskIopsCustom" span has exactly 3 attributes whose keys start with "bugsnag.device.disk.iops_"
    And the "DiskIopsCustom" span attribute "bugsnag.device.disk.iops_read" is encoded as intValue
    And the "DiskIopsCustom" span attribute "bugsnag.device.disk.iops_write" is encoded as intValue
    And the "DiskIopsCustom" span attribute "bugsnag.device.disk.iops_total" is encoded as intValue
    And the "DiskIopsCustom" span has none of the following attributes:
      | bugsnag.app.disk.bytes_read         |
      | bugsnag.app.disk.bytes_written      |
      | bugsnag.app.disk.read_bytes_per_sec |
      | bugsnag.app.disk.write_bytes_per_sec |
      | bugsnag.app.disk.ops_per_sec        |
      | syscr                               |
      | syscw                               |
      | read_bytes                          |
      | write_bytes                         |
      | rchar                               |
      | wchar                               |

# Scenario 13 (disk IOPS does not affect existing system metrics)
  Scenario: CPU and memory system metrics remain present alongside disk IOPS
    When I run "DiskIopsIsolationScenario" configured as "enabled"
    And I wait to receive a span named "[AppSession/DiskIopsIsolation]"
    Then the "[AppSession/DiskIopsIsolation]" span has integer attribute named "bugsnag.device.disk.iops_read"
    And the "[AppSession/DiskIopsIsolation]" span has integer attribute named "bugsnag.device.disk.iops_write"
    And the "[AppSession/DiskIopsIsolation]" span has integer attribute named "bugsnag.device.disk.iops_total"
    And the "[AppSession/DiskIopsIsolation]" span has double attribute named "bugsnag.system.cpu_min_total"
    And the "[AppSession/DiskIopsIsolation]" span has int attribute named "bugsnag.system.memory.spaces.device.min"

  Scenario: CPU and memory system metrics remain present when disk IOPS is disabled
    When I run "DiskIopsIsolationScenario" configured as "disabled"
    And I wait to receive a span named "[AppSession/DiskIopsIsolation]"
    Then the "[AppSession/DiskIopsIsolation]" span has no "bugsnag.device.disk.iops_read" attribute
    And the "[AppSession/DiskIopsIsolation]" span has no "bugsnag.device.disk.iops_write" attribute
    And the "[AppSession/DiskIopsIsolation]" span has no "bugsnag.device.disk.iops_total" attribute
    And the "[AppSession/DiskIopsIsolation]" span has double attribute named "bugsnag.system.cpu_min_total"
    And the "[AppSession/DiskIopsIsolation]" span has int attribute named "bugsnag.system.memory.spaces.device.min"


# Scenario 14 (mixed SDK versions – partial disk IOPS coverage)
  Scenario: Mixed disk-on and disk-off spans are delivered in one batch
    When I run "DiskIopsMixedScenario"
    And I wait to receive a span named "DiskIopsNewSdk"
    And I wait to receive a span named "DiskIopsOldSdk"
    Then the "DiskIopsNewSdk" span has integer attribute named "bugsnag.device.disk.iops_read"
    And the "DiskIopsNewSdk" span has integer attribute named "bugsnag.device.disk.iops_write"
    And the "DiskIopsNewSdk" span has integer attribute named "bugsnag.device.disk.iops_total"
    And the "DiskIopsNewSdk" span has double attribute named "bugsnag.system.cpu_mean_total"
    And the "DiskIopsOldSdk" span has no "bugsnag.device.disk.iops_read" attribute
    And the "DiskIopsOldSdk" span has no "bugsnag.device.disk.iops_write" attribute
    And the "DiskIopsOldSdk" span has no "bugsnag.device.disk.iops_total" attribute
    And the "DiskIopsOldSdk" span has double attribute named "bugsnag.system.cpu_mean_total"

# Scenario 15 (SDK delivers span payload to trace API with disk IOPS)
  Scenario: SDK delivers a disk IOPS span payload to the trace API
    When I run "DiskIopsScenario" configured as "custom"
    And I wait to receive a span named "DiskIopsCustom"
    Then the "DiskIopsCustom" span has integer attribute named "bugsnag.device.disk.iops_read"
    And the "DiskIopsCustom" span has integer attribute named "bugsnag.device.disk.iops_write"
    And the "DiskIopsCustom" span has integer attribute named "bugsnag.device.disk.iops_total"
    And the trace payload field "resourceSpans.0.resource" string attribute "telemetry.sdk.name" equals "bugsnag.performance.android"