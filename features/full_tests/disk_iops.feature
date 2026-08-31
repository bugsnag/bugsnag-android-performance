Feature: Disk IOPS

  # ROAD 2233 – Scenario 1
  # SDK emits all 3 disk IOPS attributes as doubleValue on eligible spans.
  Scenario Outline: SDK emits all 3 disk IOPS attributes as doubleValue on eligible spans
    When I run "DiskIopsScenario" configured as "<span_type>"
    And I wait to receive a span named "<span_name>"
    Then the "<span_name>" span has double attribute named "bugsnag.device.disk.iops_read"
    And the "<span_name>" span has double attribute named "bugsnag.device.disk.iops_write"
    And the "<span_name>" span has double attribute named "bugsnag.device.disk.iops_total"
    And the "<span_name>" span double attribute "bugsnag.device.disk.iops_total" equals the sum of "bugsnag.device.disk.iops_read" and "bugsnag.device.disk.iops_write"

    Examples:
      | platform | span_type   | span_name             |
      | android  | custom      | DiskIopsCustom        |
      | android  | app_session | [AppSession/DiskIops] |

  # ROAD 2233 – Scenario 1 (app_start)
  @skip_below_android_10
  Scenario: SDK emits all 3 disk IOPS attributes as doubleValue on app_start spans
    Given I run "DiskIopsAppStartScenario"
    Then I relaunch the app after shutdown
    And I load scenario "DiskIopsAppStartScenario"
    And I wait to receive a span named "[AppStart/AndroidCold]SplashScreen"
    Then the "[AppStart/AndroidCold]SplashScreen" span has double attribute named "bugsnag.device.disk.iops_read"
    And the "[AppStart/AndroidCold]SplashScreen" span has double attribute named "bugsnag.device.disk.iops_write"
    And the "[AppStart/AndroidCold]SplashScreen" span has double attribute named "bugsnag.device.disk.iops_total"
    And the "[AppStart/AndroidCold]SplashScreen" span double attribute "bugsnag.device.disk.iops_total" equals the sum of "bugsnag.device.disk.iops_read" and "bugsnag.device.disk.iops_write"

  # ROAD 2233 – Scenario 2
  # Exact IOPS formula with fixed start/end counters is NOT implementable as a Maze scenario.
  #
  # Why Maze cannot cover this:
  # - The Scenario Outline examples require injecting known /proc/self/io counter values
  #   (e.g. syscr 1200→1260, syscw 400→430) over a fixed duration (e.g. 2.0s) and asserting
  #   exact doubles (read=30.0, write=15.0, total=45.0).
  # - On a real device/emulator, syscr/syscw are owned by the kernel and advance with all
  #   process I/O. Maze cannot set or freeze those counters at span start/end.
  # - Real-device IOPS therefore vary by device, OS, and background activity, so exact expected
  #   values would be flaky even if approximate I/O were forced in the fixture.
  # - Maze only observes the delivered OTLP payload; it has no hook to stub ProcIoReader or
  #   SystemClock.elapsedRealtimeNanos inside the production SDK path.
  #
  # Covered instead by unit tests with injectable io fixtures + mocked clocks:
  #   DiskIoMetricsFormulaTest (android_true_iops, android_zero_activity)
  # Formula under test: iops = (sysc*_end - sysc*_start) / durationSec ; total = read + write
  # iOS 16KB-block rows from the ROAD table are out of scope for this Android repository.

  # ROAD 2233 – Scenario 3 (ED 3.1.4 – omit disk metrics when duration is zero or negative)
  # Invalid-duration fallback is NOT implementable as a Maze scenario.
  #
  # Why Maze cannot cover this:
  # - The examples require forcing span metric timestamps such that endNs == startNs (zero
  #   duration) or endNs < startNs (negative duration), e.g.
  #   start=1681383000000000000 / end=1681383000000000000, and
  #   start=1681383000150000000 / end=1681383000000000000.
  # - Disk IOPS duration is computed from SystemClock.elapsedRealtimeNanos() snapshots taken
  #   inside DiskIoMetricsSource, not from Maze-controlled span fields. Maze cannot rewind or
  #   set the device clock between createStartMetrics() and endMetrics().
  # - A normal Maze span always has a positive wall-clock duration, so the production path
  #   never hits the durationNanos <= 0 guard during E2E runs.
  # - ED still requires: omit disk attrs, do not crash, and still deliver the span — that
  #   guard logic needs deterministic clock injection, which only unit tests provide.
  #
  # Covered instead by unit tests with mocked SystemClock:
  #   DiskIoMetricsInvalidDurationTest (zero_duration, negative_duration)
  # Asserts: no bugsnag.device.disk.iops_* attributes, span still ends, no crash.

  # ROAD 2233 – Scenario 4 (ED §3.1.4 – negative deltas clamped OR treated as invalid)
  #
  # Validity vs the ROAD Scenario Outline:
  # - The outline text ("clamped to zero for all dimensions") and Expected columns assume
  #   per-dimension clamping, e.g. only-read-regresses → read=0.0, write=15.0, total=15.0
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
  # | none (idle)   | 0.0           | 0.0            | 0.0            |
  # | read-only     | > 0           | 0.0            | equals read    |
  # | write-only    | 0.0           | > 0            | equals write   |
  #
  # Unlike Scenario 5 (unavailable source → omit attrs), a valid counter source with
  # unchanged counters must still emit all three attributes as 0.0.
  #
  # Why Maze cannot cover this reliably:
  # - Requires asserting exact 0.0 on one or all dimensions (idle, read-only, write-only).
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
  # Asserts: all bugsnag.device.disk.iops_* attributes present; exact doubles per table.

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
  # Scenario Outline: Disk IOPS is captured correctly across app lifecycle transitions.
  #
  # Android examples (valid per ED):
  # | Initial State | Transition                         | Notes                          |
  # | foreground    | background then foreground         | mid-span background transition |
  # | foreground    | background (app stays background)  | span ends while in background  |
  # | background    | foreground                         | span starts in background      |
  # | foreground    | app terminated by system           | orphaned span on termination   |
  #
  # Android behaviour:
  # - DiskIoMetricsSource does not pause/resume or branch on foreground state. IOPS depends
  #   only on syscr/syscw at span start/end and elapsedRealtimeNanos between those points.
  # - Lifecycle rows verify transitions do not corrupt the metric path; termination without
  #   span.end() means disk attrs are not applied (overlaps Scenario 8 orphan semantics).
  #
  # Why Maze cannot cover this reliably:
  # - Background/foreground transitions are possible in Maze fixtures, but exact IOPS still
  #   requires deterministic /proc/self/io counters at span start/end.
  # - Background I/O rates vary by device/OS; exact Expected Read/Write/Total would be flaky.
  # - App termination mid-span cannot assert delivered IOPS in Maze (span never completes).
  #
  # Covered instead by unit tests with injectable io fixtures + mocked clocks:
  #   DiskIoMetricsLifecycleTest
  #     (mid_span_background_transition, ends_while_in_background, starts_in_background,
  #      orphaned_on_termination)
  # Asserts: completed spans get correct IOPS regardless of lifecycle timing; terminated span
  #          has no disk attrs until finish.

  # ROAD 2233 – Scenario 10 (spans from older SDK without disk IOPS)
  # Scenario: Spans from older SDK without disk IOPS are accepted and stored correctly.
  #
  # Full ROAD coverage includes pipeline (-1 default) and API (null) behaviour, which are
  # backend concerns outside this Android SDK repository.
  #
  # Android SDK scope (valid here):
  # - Spans with no disk metric source emit no bugsnag.device.disk.iops_* attributes.
  # - Other span attributes (e.g. CPU, fps) are unaffected when disk source is absent.
  # - OTLP JSON payload omits disk IOPS keys entirely (no null doubleValue sent).
  #
  # Covered by unit tests:
  #   DiskIoMetricsLegacySdkTest
  #     (spanWithoutDiskMetricsOmitsDiskAttrsFromPayload,
  #      otherMetricsUnaffectedWhenDiskSourceAbsent)

  # ROAD 2233 – Scenario 11 (high and burst I/O – valid Float64)
  # Scenario Outline: Disk IOPS values are valid Float64 under high and burst I/O conditions.
  #
  # Android examples (valid per ED):
  # | Workload                          | Duration Sec | Notes                              |
  # | intensive SQLite (1000+ queries)  | 2.0          | high IOPS, large but valid value   |
  # | 10MB burst write then idle        | 10.0         | burst averaged over full duration  |
  # | 50MB file copy                    | 5.0          | both read and write > 0            |
  #
  # Asserts: finite doubles (not NaN/Infinity); formula averages burst over full span duration.
  #
  # Why Maze cannot cover this reliably:
  # - Real SQLite/file-copy workloads produce device-dependent syscr/syscw rates.
  # - Exact high-IOPS values cannot be asserted deterministically on emulator/device.
  #
  # Covered instead by unit tests with injectable io fixtures + mocked clocks:
  #   DiskIoMetricsHighIopsTest
  #     (intensive_sqlite, burst_write_then_idle, large_file_copy)
  #   DiskIoMetricsHighIopsJsonTest (serializesHighIopsAsDoubleValueInJsonPayload)
  # Asserts: finite Float64 values; JSON encodes as doubleValue (not null).

  # ROAD 2233 – Scenario 12 (OTLP payload structure for disk IOPS)
  # Scenario: OTLP payload contains exactly 3 disk attributes with correct keys and structure.
  #
  # Validates:
  # - Exactly these case-sensitive keys under resourceSpans.scopeSpans.spans.attributes:
  #     bugsnag.device.disk.iops_read
  #     bugsnag.device.disk.iops_write
  #     bugsnag.device.disk.iops_total
  # - Each value nested as attributes[].value.doubleValue
  # - No legacy keys (bugsnag.app.disk.bytes_read, bytes_written, read_bytes_per_sec,
  #   write_bytes_per_sec, ops_per_sec)
  # - No raw /proc counter attribute keys (syscr, syscw, read_bytes, etc.)
  #
  # Maze Scenario 1 asserts attribute presence on delivered spans; full OTLP nesting and
  # legacy-key exclusion are covered by unit tests against TracePayload.encodeSpanPayload:
  #   DiskIoMetricsOtlpPayloadTest (otlpPayloadContainsExactlyThreeDiskIopsAttributes)

  # ROAD 2233 – Scenario 13 (disk IOPS does not affect existing system metrics)
  # Scenario Outline: Existing system metrics are unaffected by disk IOPS collection.
  #
  # Disk State examples:
  # | enabled with valid disk data  | disk IOPS attrs present with non-zero values |
  # | disabled (source unavailable) | disk source absent; no disk IOPS attrs       |
  # | emitting zero IOPS values     | disk IOPS attrs present as 0.0               |
  #
  # In all cases CPU, memory, and frozen-frame attributes must remain present and correct.
  # SpanMetricsSnapshot invokes each MetricSource independently; disk collection does not
  # modify or suppress cpu/memory/rendering endMetrics paths.
  #
  # Covered by unit tests with stub metric sources + injectable disk fixtures:
  #   DiskIoMetricsSystemMetricsIsolationTest
  #     (enabled_with_valid_disk_data, disabled_source_unavailable, emitting_zero_iops_values)
  # Asserts: bugsnag.system.cpu_mean_total, bugsnag.system.memory.spaces.device.mean, and
  #          bugsnag.rendering.frozen_frames unchanged across all disk states.

  # ROAD 2233 – Scenario 14 (mixed SDK versions – partial disk IOPS coverage)
  # Scenario: Project with mixed SDK versions reports partial disk IOPS coverage correctly.
  #
  # Full ROAD example (backend/API scope):
  # - 1240 spans in a group: 980 new SDK (with disk IOPS), 260 old SDK (without)
  # - disk.iops_total.span_count == 980
  # - Percentiles (p50–p99) computed from 980 reporting spans only
  # - CPU/memory statistics reflect all 1240 spans
  #
  # Why this is not covered in Maze or full-scale unit tests here:
  # - span_count and percentile aggregation happen in the Bugsnag pipeline/API, not the SDK.
  # - The Android SDK only controls per-span OTLP attributes (present vs absent).
  #
  # Android SDK scope (valid here):
  # - Old-SDK spans omit bugsnag.device.disk.iops_* (Scenario 10)
  # - New-SDK spans include disk IOPS in the same batch
  # - CPU attrs can be present on every span regardless of disk reporting
  #
  # Covered by unit test (98:26 scale model of 980:260):
  #   DiskIoMetricsMixedSdkPayloadTest (mixedSdkBatchIncludesDiskIopsOnlyOnNewSdkSpans)
  # Asserts: 98/124 spans report disk IOPS in OTLP; 124/124 report CPU; payload schema valid.
  # Backend tests must verify span_count and percentiles separately.

  # ROAD 2233 – Scenario 15 (SDK delivers span payload to trace API with disk IOPS)
  # Scenario: SDK delivers span payload to trace API successfully with disk IOPS attributes.
  #
  # Example attribute values (ROAD):
  #   bugsnag.device.disk.iops_read  = 18.0
  #   bugsnag.device.disk.iops_write = 16.4
  #   bugsnag.device.disk.iops_total = 34.4
  #
  # Validation:
  # - OTLP payload is POSTed to the trace API via HttpDelivery
  # - Trace API responds with HTTP 200 (accepted)
  #
  # Why not Maze: exact IOPS values require injectable /proc/self/io fixtures; delivery +
  # HTTP status are covered by unit test with mocked HttpURLConnection.
  #
  # Covered by unit test:
  #   DiskIoMetricsHttpDeliveryTest (deliversSpanPayloadWithDiskIopsAttributesToTraceApi)
  # Asserts: gzip OTLP body contains the three disk IOPS attrs; DeliveryResult.Success on 200.
