Feature: Disk IOPS

  # ROAD 2233 – Scenario 1
  # SDK emits all 3 disk IOPS attributes as IntValue on eligible spans.
  #
  # Covered by unit tests:
  #   DiskIoMetricsSourceTest (endMetricsSetsIopsAttributesOnSpan)
  #   DiskIoMetricsOtlpPayloadTest (otlpPayloadContainsExactlyThreeDiskIopsAttributes)
  Scenario Outline: SDK emits all 3 disk IOPS attributes as IntValue on eligible spans
    When I run "DiskIopsScenario" configured as "<span_type>"
    And I wait to receive a span named "<span_name>"
    Then the "<span_name>" span has integer attribute named "bugsnag.device.disk.iops_read"
    And the "<span_name>" span has integer attribute named "bugsnag.device.disk.iops_write"
    And the "<span_name>" span has integer attribute named "bugsnag.device.disk.iops_total"
    And the "<span_name>" span integer attribute "bugsnag.device.disk.iops_total" equals the sum of "bugsnag.device.disk.iops_read" and "bugsnag.device.disk.iops_write"

    Examples:
      | platform | span_type   | span_name             |
      | android  | custom      | DiskIopsCustom        |
      | android  | app_session | [AppSession/DiskIops] |

  # ROAD 2233 – Scenario 1 (app_start)
  # Covered by unit tests:
  #   DiskIoMetricsSourceTest (endMetricsSetsIopsAttributesOnSpan)
  #   DiskIoMetricsOtlpPayloadTest (otlpPayloadContainsExactlyThreeDiskIopsAttributes)
  @skip_below_android_10
  Scenario: SDK emits all 3 disk IOPS attributes as IntValue on app_start spans
    Given I run "DiskIopsAppStartScenario"
    Then I relaunch the app after shutdown
    And I load scenario "DiskIopsAppStartScenario"
    And I wait to receive a span named "[AppStart/AndroidCold]SplashScreen"
    Then the "[AppStart/AndroidCold]SplashScreen" span has integer attribute named "bugsnag.device.disk.iops_read"
    And the "[AppStart/AndroidCold]SplashScreen" span has integer attribute named "bugsnag.device.disk.iops_write"
    And the "[AppStart/AndroidCold]SplashScreen" span has integer attribute named "bugsnag.device.disk.iops_total"
    And the "[AppStart/AndroidCold]SplashScreen" span integer attribute "bugsnag.device.disk.iops_total" equals the sum of "bugsnag.device.disk.iops_read" and "bugsnag.device.disk.iops_write"

  # ROAD 2233 – Scenario 2
  # Maze cannot freeze /proc/self/io or the clock, so it cannot assert the ROAD table
  # numbers (30/15/45). It runs the real DiskIoMetricsSource path and checks the values
  # the SDK actually computed on the device: integers >= 0, total = read + write, and
  # end syscall counts are not less than start (read and write).
  #
  # Covered by unit test with injectable io fixtures + mocked clocks:
  #   DiskIoMetricsFormulaTest
  #     (computesIopsUsingSyscrSyscwFormula_androidTrueIops,
  #      computesIopsUsingSyscrSyscwFormula_androidZeroActivity)
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

  # ROAD 2233 – Scenario 3 (ED 3.1.4 – omit disk metrics when duration is zero or negative)
  # Maze cannot rewind the device clock, so InternalDebug.diskIoTimestampFault makes
  # DiskIoMetricsSource treat duration as zero or negative. The real collector still runs:
  # the span is delivered, iops_* are omitted, skip_reason starts with invalid_duration.
  #
  # Covered by unit test with mocked SystemClock:
  #   DiskIoMetricsInvalidDurationTest
  #     (zero_duration, negative_duration)
  Scenario Outline: SDK omits disk IOPS when span duration is invalid
    Given I load scenario "DiskIopsScenario"
    And I configure scenario "span_type" to "<span_type>"
    And I configure scenario "duration_fault" to "<duration_fault>"
    And I run the loaded scenario
    And I wait to receive a span named "<span_name>"
    Then the "<span_name>" span string attribute "bugsnag.internal.disk_io.end_metrics_called" equals "true"
    And the "<span_name>" span string attribute "bugsnag.internal.disk_io.skip_reason" starts with "invalid_duration"
    And the "<span_name>" span has no "bugsnag.device.disk.iops_read" attribute
    And the "<span_name>" span has no "bugsnag.device.disk.iops_write" attribute
    And the "<span_name>" span has no "bugsnag.device.disk.iops_total" attribute

    Examples:
      | platform | span_type   | duration_fault | span_name             |
      | android  | custom      | zero           | DiskIopsCustom        |
      | android  | custom      | negative       | DiskIopsCustom        |
      | android  | app_session | zero           | [AppSession/DiskIops] |
      | android  | app_session | negative       | [AppSession/DiskIops] |

  # ROAD 2233 – Scenario 4 (ED §3.1.4 – negative deltas clamped OR treated as invalid)
  #
  # Validity vs the ROAD Scenario Outline:
  # - The outline text ("clamped to zero for all dimensions") and Expected columns assume
  #   per-dimension clamping, e.g. only-read-regresses → read=0, write=15, total=15
  #   (over a 2.0s duration). That is ONE allowed ED interpretation.
  # - Android does NOT clamp: if readDelta < 0 OR writeDelta < 0, DiskIoMetricsSource returns
  #   early and omits ALL disk iops_* attributes. That is the other ED-allowed path
  #   ("treated as invalid") and still guarantees no negative values are emitted.
  # - Therefore the ROAD Expected Read/Write/Total numbers are NOT valid assertions for
  #   this Android SDK as implemented. The acceptance intent (no negatives emitted; span
  #   still delivered) IS valid and is what we test.
  #
  # Why Maze cannot cover this:
  # - Requires injecting regressing /proc/self/io counters (end < start for syscr and/or
  #   syscw). Kernel counters are monotonic on a real device; Maze cannot force regression.
  #
  # Covered instead by unit tests with injectable io fixtures:
  #   DiskIoMetricsNegativeDeltaTest
  #     (both_counters_regress, only_read_regresses, only_write_regresses)
  # Asserts: all bugsnag.device.disk.iops_* attributes absent; span still ends; no crash.

  # ROAD 2233 – Scenario 5 (ED §3.1.4 – omit disk metrics when counter source unavailable)
  # Scenario Outline: Disk metrics are omitted gracefully when counter source is unavailable.
  #
  # Android examples (valid per ED):
  # - /proc/self/io is unreadable
  # - /proc/self/io missing syscr field (and similarly missing syscw)
  # - /proc/self/io contains non-numeric counter values
  # iOS examples (proc_pid_rusage failure / unavailable) are out of scope for this repo.
  #
  # Why Maze cannot cover this reliably:
  # - Forcing /proc/self/io to be unreadable, missing fields, or malformed on a real device
  #   is not controllable from the Maze fixture without replacing the production reader.
  # - Maze only sees the delivered span payload; it cannot inject ProcIoReader failure modes.
  #
  # Covered instead by unit tests with injectable fixtures:
  #   DiskIoMetricsUnavailableSourceTest
  #     (proc_io_unreadable, proc_io_missing_syscr, proc_io_non_numeric)
  #   ProcIoReaderFailureTest (parseNonExistentFile, missing fields, non-numeric)
  # Asserts: no bugsnag.device.disk.iops_* attributes; span still ends; no crash.

  # ROAD 2233 – Scenario 6 (zero and asymmetric disk activity)
  # Scenario Outline: Disk IOPS attributes are emitted correctly for zero and asymmetric activity.
  #
  # Android examples (valid per ED):
  # | Activity Type | Expected Read | Expected Write | Expected Total |
  # | none (idle)   | 0             | 0              | 0              |
  # | read-only     | > 0           | 0              | equals read    |
  # | write-only    | 0             | > 0            | equals write   |
  #
  # Unlike Scenario 5 (unavailable source → omit attrs), a valid counter source with
  # unchanged counters must still emit all three attributes as 0.
  #
  # Why Maze cannot cover this reliably:
  # - Requires asserting exact 0 on one or all dimensions (idle, read-only, write-only).
  # - On a real device/emulator, syscr/syscw advance with framework and background I/O even
  #   when the fixture performs no (or only read / only write) app-level file operations.
  # - Maze cannot inject /proc/self/io counter values or freeze kernel counters at span
  #   start/end, so exact Expected Read/Write/Total values would be flaky.
  #
  # Covered instead by unit tests with injectable io fixtures + mocked clocks:
  #   DiskIoMetricsAsymmetricActivityTest
  #     (none_idle, read_only, write_only)
  # Also overlaps with DiskIoMetricsFormulaTest (android_zero_activity) and
  # DiskIoMetricsSourceTest (endMetricsSetsZeroIopsWhenCountersUnchanged) for idle.
  # Asserts: all bugsnag.device.disk.iops_* attributes present; exact integers per table.

  # ROAD 2233 – Scenario 7 (concurrent spans – independent IOPS, no snapshot collision)
  # Scenario: Concurrent spans each compute independent disk IOPS without collision.
  #
  # Expected flow:
  # - Span A starts at T0, Span B starts at T1 (T1 > T0)
  # - Span B ends at T2, Span A ends at T3
  # - Span B iops_total covers (T2 - T1) only; Span A iops_total covers (T3 - T0) only
  # - Each span holds its own DiskIoSnapshot via SpanMetricsSnapshot; values are not shared
  #
  # Why Maze cannot cover this reliably:
  # - Requires controlling syscr/syscw at four precise timestamps (T0–T3) for overlapping spans.
  # - Maze cannot inject /proc/self/io reads or freeze kernel counters between span start/end.
  # - Overlapping custom spans with deterministic counter progression is not reproducible E2E.
  #
  # Covered instead by unit tests with injectable io fixtures + mocked clocks:
  #   DiskIoMetricsConcurrentSpansTest
  #     (overlappingSpansComputeIndependentDiskIops,
  #      finishingNestedSpanDoesNotAffectOuterSpanMetrics)
  # Asserts: each span gets independent IOPS; finishing inner span does not corrupt outer span.

  # ROAD 2233 – Scenario 7b (sequential spans – fresh snapshots, no stale start counters)
  # Maze cannot control overlapping concurrent counters (Scenario 7), but consecutive spans
  # are reproducible E2E: each span creates its own DiskIoSnapshot at start. Span 2's
  # start counters must be >= Span 1's end counters (monotonic /proc counters), proving
  # Span 2 did not reuse Span 1's start snapshot.
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

  # ROAD 2233 – Scenario 8 (orphaned span snapshots – no corruption of normal spans)
  # Scenario: Orphaned span snapshot does not cause memory leak / corrupt completed spans.
  #
  # Expected flow:
  # - 50 spans start but never end (orphaned SpanMetricsSnapshot retained on SpanImpl)
  # - 100 additional spans start and end normally
  # - All 100 completed spans report correct disk IOPS; no crash
  #
  # Android behaviour:
  # - DiskIoMetricsSource does not register snapshots in global state; each snapshot lives on
  #   its SpanMetricsSnapshot until finish or span GC. Orphaned snapshots are span-scoped
  #   retention, not unbounded accumulation in the metrics source.
  #
  # Why Maze cannot cover this reliably:
  # - Requires 150 spans with deterministic syscr/syscw at each start/end (100 completed).
  # - OOM assertion with 50 orphans is not meaningful or reproducible in CI (overhead is tiny).
  # - Maze cannot inject /proc/self/io or assert exact IOPS on 100 spans E2E.
  #
  # Covered instead by unit tests with injectable io fixtures + mocked clocks:
  #   DiskIoMetricsOrphanedSpansTest
  #     (completedSpansReportCorrectDiskIopsWithOrphanedSnapshotsPresent)
  # Asserts: 50 orphaned snapshots present; 100 completed spans each get read=10, write=5, total=15.

# ROAD 2233 – Scenario 9 (disk IOPS across app lifecycle transitions)
  # DiskIoMetricsSource does not pause on foreground state. Maze cannot assert exact IOPS
  # (counters keep moving) or termination-without-end (the span is never delivered).
  # mid_span_bg_fg uses Appium background/foreground (programmatic resume is blocked on
  # modern Android). ends/starts_in_background send HOME from the fixture.
  #
  # Covered by unit tests with injectable io fixtures + mocked clocks:
  #   DiskIoMetricsLifecycleTest
  #     (mid_span_background_transition, ends_while_in_background, starts_in_background,
  #      orphaned_on_termination)
  Scenario Outline: SDK captures disk IOPS across mid-span background/foreground
    Given I load scenario "DiskIopsLifecycleScenario"
    And I configure scenario "span_type" to "<span_type>"
    And I configure scenario "transition" to "mid_span_bg_fg"
    And I run the loaded scenario
    And I send the app to the background for 2 seconds
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
  # Pipeline (-1 default) and API (null) behaviour are backend concerns.
  # Android SDK scope: EnabledMetrics.disk = false omits iops_* even if SpanMetrics asks for disk.
  #
  # Covered by unit tests:
  #   DiskIoMetricsLegacySdkTest
  Scenario: SDK omits disk IOPS when disk metrics are disabled
    When I run "DiskIopsDisabledScenario"
    And I wait to receive a span named "DiskIopsDisabled"
    Then the "DiskIopsDisabled" span has no "bugsnag.device.disk.iops_read" attribute
    And the "DiskIopsDisabled" span has no "bugsnag.device.disk.iops_write" attribute
    And the "DiskIopsDisabled" span has no "bugsnag.device.disk.iops_total" attribute

  # ROAD 2233 – Scenario 11 (high and burst I/O – valid Int64)
  # Real SQLite / file workloads cannot yield the ROAD table numbers on a device.
  # Maze runs those workloads against the real collector and checks valid integers.
  #
  # Covered instead by unit tests with injectable io fixtures + mocked clocks:
  #   DiskIoMetricsHighIopsTest
  #   DiskIoMetricsHighIopsJsonTest
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

  # ROAD 2233 – Scenario 12 (OTLP payload structure for disk IOPS)
  # Maze Scenario 1 already asserts attribute presence. This checks OTLP nesting: exactly the
  # three iops_* keys, intValue encoding, and no legacy / raw counter keys.
  #
  # Covered by unit tests:
  #   DiskIoMetricsOtlpPayloadTest (otlpPayloadContainsExactlyThreeDiskIopsAttributes)
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

  # ROAD 2233 – Scenario 13 (disk IOPS does not affect existing system metrics)
  # Frozen-frame attrs require view frames and stay unit-tested. Maze checks CPU + memory
  # on a first-class custom span with disk enabled or disabled.
  #
  # Covered by unit tests:
  #   DiskIoMetricsSystemMetricsIsolationTest
  Scenario: Existing system metrics are present when disk IOPS is enabled
    When I run "DiskIopsIsolationScenario" configured as "enabled"
    And I wait to receive a span named "DiskIopsIsolation"
    Then the "DiskIopsIsolation" span has integer attribute named "bugsnag.device.disk.iops_read"
    And the "DiskIopsIsolation" span has integer attribute named "bugsnag.device.disk.iops_write"
    And the "DiskIopsIsolation" span has integer attribute named "bugsnag.device.disk.iops_total"
    And the "DiskIopsIsolation" span has double attribute named "bugsnag.system.cpu_mean_total"
    And the "DiskIopsIsolation" span has int attribute named "bugsnag.system.memory.spaces.device.mean"

  Scenario: Existing system metrics are present when disk IOPS is disabled
    When I run "DiskIopsIsolationScenario" configured as "disabled"
    And I wait to receive a span named "DiskIopsIsolation"
    Then the "DiskIopsIsolation" span has no "bugsnag.device.disk.iops_read" attribute
    And the "DiskIopsIsolation" span has no "bugsnag.device.disk.iops_write" attribute
    And the "DiskIopsIsolation" span has no "bugsnag.device.disk.iops_total" attribute
    And the "DiskIopsIsolation" span has double attribute named "bugsnag.system.cpu_mean_total"
    And the "DiskIopsIsolation" span has int attribute named "bugsnag.system.memory.spaces.device.mean"

  # ROAD 2233 – Scenario 14 (mixed SDK versions – partial disk IOPS coverage)
  # Backend span_count / percentiles are out of scope. Maze delivers one batch with a
  # disk-reporting span and a disk-omitted span; CPU is present on both.
  #
  # Covered by unit test:
  #   DiskIoMetricsMixedSdkPayloadTest
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

  # ROAD 2233 – Scenario 15 (SDK delivers span payload to trace API with disk IOPS)
  # Maze mock accepts traces with HTTP 200. Exact ROAD values (18/16/34) stay unit-tested.
  #
  # Covered by unit test:
  #   DiskIoMetricsHttpDeliveryTest
  Scenario: SDK delivers a disk IOPS span payload to the trace API
    When I run "DiskIopsScenario" configured as "custom"
    And I wait to receive a span named "DiskIopsCustom"
    Then the "DiskIopsCustom" span has integer attribute named "bugsnag.device.disk.iops_read"
    And the "DiskIopsCustom" span has integer attribute named "bugsnag.device.disk.iops_write"
    And the "DiskIopsCustom" span has integer attribute named "bugsnag.device.disk.iops_total"
    And the trace payload field "resourceSpans.0.resource" string attribute "telemetry.sdk.name" equals "bugsnag.performance.android"