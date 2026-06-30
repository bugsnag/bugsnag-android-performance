Feature: Foreground App Session Span

Scenario: App session span sends app-session attributes
    Given I load scenario "AppSessionResourceUsageScenario"
    And I configure bugsnag "memoryMetrics" to "true"
    And I configure bugsnag "cpuMetrics" to "false"
    And I configure scenario "session_type" to "TestManualSpan"
    And I start bugsnag
    And I run the loaded scenario
    And I wait to receive 1 trace
    Then a span field "name" equals "TestManualSpan"

Scenario: App session span contains memory aggregate attributes
    Given I load scenario "AppSessionResourceUsageScenario"
    And I configure bugsnag "memoryMetrics" to "true"
    And I configure scenario "session_type" to "memory session"
    And I configure scenario "span_duration" to "2.5"
    And I configure scenario "variant_name" to "Memory"
    And I start bugsnag
    And I run the loaded scenario
    And I wait to receive 1 trace
    Then a span field "name" equals "[AppSession/memory session]"
    * a span string attribute "bugsnag.span.category" equals "app_session"
    * a span bool attribute "bugsnag.span.first_class" is true
    * a span integer attribute "bugsnag.system.memory.spaces.device.size" is greater than or equal to 0
    * a span integer attribute "bugsnag.system.memory.spaces.device.mean" is greater than or equal to 0
    * a span integer attribute "bugsnag.system.memory.spaces.device.min" is greater than or equal to 0
    * a span integer attribute "bugsnag.system.memory.spaces.device.max" is greater than or equal to 0
    * a span integer attribute "bugsnag.system.memory.spaces.device.min" is less than or equal to span integer attribute "bugsnag.system.memory.spaces.device.max"


Scenario: App session span contains CPU aggregate attributes
    Given I load scenario "AppSessionResourceUsageScenario"
    And I configure bugsnag "cpuMetrics" to "true"
    And I configure bugsnag "memoryMetrics" to "false"
    And I configure scenario "session_type" to "CPUSession"
    And I configure scenario "span_duration" to "4.0"
    And I configure scenario "work_duration" to "3.0"
    And I configure scenario "work_on_thread" to "main"
    And I configure scenario "variant_name" to "CPU"
    And I start bugsnag
    And I run the loaded scenario
    And I wait to receive 1 trace
    Then a span field "name" equals "[AppSession/CPUSession]"
    * a span string attribute "bugsnag.span.category" equals "app_session"
    * a span bool attribute "bugsnag.span.first_class" is true
    * a span float attribute "bugsnag.system.cpu_mean_total" is greater than or equal to 0.0
    * a span float attribute "bugsnag.system.cpu_mean_total" is less than or equal to 100.0
    * a span float attribute "bugsnag.system.cpu_min_total" is greater than or equal to 0.0
    * a span float attribute "bugsnag.system.cpu_min_total" is less than or equal to 100.0
    * a span float attribute "bugsnag.system.cpu_max_total" is greater than or equal to 0.0
    * a span float attribute "bugsnag.system.cpu_max_total" is less than or equal to 100.0
    * a span float attribute "bugsnag.system.cpu_min_total" is less than or equal to span float attribute "bugsnag.system.cpu_max_total"

Scenario: App session type is NOT sanitized
    Given I load scenario "AppSessionResourceUsageScenario"
    And I configure bugsnag "memoryMetrics" to "true"
    And I configure scenario "session_type" to "user checkout-flow"
    And I configure scenario "span_duration" to "2.0"
    And I configure scenario "variant_name" to "Sanitized"
    And I start bugsnag
    And I run the loaded scenario
    And I wait to receive 1 trace
    Then a span field "name" equals "[AppSession/user checkout-flow]"
    * a span string attribute "bugsnag.span.category" equals "app_session"
    * a span string attribute "bugsnag.app_session.name" equals "user checkout-flow"

Scenario: App session span does not contain resource usage when metrics are disabled
    Given I load scenario "AppSessionResourceUsageScenario"
    And I configure bugsnag "memoryMetrics" to "false"
    And I configure bugsnag "cpuMetrics" to "false"
    And I configure scenario "session_type" to "disabled metrics"
    And I configure scenario "span_duration" to "2.0"
    And I configure scenario "variant_name" to "MetricsDisabled"
    And I start bugsnag
    And I run the loaded scenario
    And I wait to receive 1 trace
    Then a span field "name" equals "[AppSession/disabled metrics]"
    * a span string attribute "bugsnag.span.category" equals "app_session"
    * every span attribute "bugsnag.system.memory.spaces.device.mean" does not exist
    * every span attribute "bugsnag.system.memory.spaces.device.min" does not exist
    * every span attribute "bugsnag.system.memory.spaces.device.max" does not exist
    * every span attribute "bugsnag.system.cpu_mean_total" does not exist
    * every span attribute "bugsnag.system.cpu_min_total" does not exist
    * every span attribute "bugsnag.system.cpu_max_total" does not exist

Scenario: Normal custom span is not treated as app session span
    Given I load scenario "AppSessionResourceUsageScenario"
    And I configure bugsnag "memoryMetrics" to "true"
    And I configure scenario "session_type" to "TestManualSpan"
    And I start bugsnag
    And I run the loaded scenario
    And I wait to receive 1 trace
    Then the "TestManualSpan" span string attribute "bugsnag.span.category" equals "custom"
    * the "TestManualSpan" span has no "bugsnag.app_session.name" attribute

Scenario: Aborted app session span is not sent
    Given I load scenario "AppSessionResourceUsageScenario"
    And I configure bugsnag "memoryMetrics" to "true"
    And I configure scenario "session_type" to "aborted session"
    And I configure scenario "span_duration" to "1.0"
    And I configure scenario "abort_span" to "true"
    And I configure scenario "variant_name" to "Aborted"
    And I start bugsnag
    And I run the loaded scenario
    And I wait for 5 seconds
    Then I received no span named "[AppSession/aborted session]"

Scenario: Force-terminated app session span is not sent
    Given I load scenario "AppSessionResourceUsageScenario"
    And I configure bugsnag "memoryMetrics" to "true"
    And I configure scenario "session_type" to "force killed session"
    And I configure scenario "span_duration" to "30.0"
    And I configure scenario "variant_name" to "ForceKilled"
    And I start bugsnag
    And I run the loaded scenario
    And I wait for 3 seconds
    And I close the app
    And I wait for 5 seconds
    Then I should receive no spans

Scenario: CPU and memory aggregates satisfy min <= mean <= max
    Given I load scenario "AppSessionResourceUsageScenario"
    And I configure bugsnag "memoryMetrics" to "true"
    And I configure bugsnag "cpuMetrics" to "true"
    And I configure scenario "session_type" to "full metrics session"
    And I configure scenario "span_duration" to "3.0"
    And I configure scenario "work_duration" to "2.0"
    And I configure scenario "work_on_thread" to "main"
    And I configure scenario "variant_name" to "MinMeanMax"
    And I start bugsnag
    And I run the loaded scenario
    And I wait to receive 1 trace
    Then a span field "name" equals "[AppSession/full metrics session]"
    * a span string attribute "bugsnag.span.category" equals "app_session"
    * a span bool attribute "bugsnag.span.first_class" is true
    * a span integer attribute "bugsnag.system.memory.spaces.device.min" is greater than or equal to 0
    * a span integer attribute "bugsnag.system.memory.spaces.device.mean" is greater than or equal to 0
    * a span integer attribute "bugsnag.system.memory.spaces.device.max" is greater than or equal to 0
    * a span float attribute "bugsnag.system.cpu_min_total" is greater than or equal to 0.0
    * a span float attribute "bugsnag.system.cpu_mean_total" is greater than or equal to 0.0
    * a span float attribute "bugsnag.system.cpu_max_total" is greater than or equal to 0.0
    * a span integer attribute "bugsnag.system.memory.spaces.device.min" is less than or equal to span integer attribute "bugsnag.system.memory.spaces.device.mean"
    * a span integer attribute "bugsnag.system.memory.spaces.device.mean" is less than or equal to span integer attribute "bugsnag.system.memory.spaces.device.max"
    * a span float attribute "bugsnag.system.cpu_min_total" is less than or equal to span float attribute "bugsnag.system.cpu_mean_total"
    * a span float attribute "bugsnag.system.cpu_mean_total" is less than or equal to span float attribute "bugsnag.system.cpu_max_total"

Scenario: Single sample produces min equals max equals mean
    Given I load scenario "AppSessionResourceUsageScenario"
    And I configure bugsnag "memoryMetrics" to "true"
    And I configure bugsnag "cpuMetrics" to "true"
    And I configure scenario "session_type" to "single sample session"
    And I configure scenario "span_duration" to "0.1"
    And I configure scenario "work_duration" to "0.05"
    And I configure scenario "work_on_thread" to "main"
    And I configure scenario "variant_name" to "SingleSample"
    And I start bugsnag
    And I run the loaded scenario
    And I wait to receive 1 trace
    Then a span field "name" equals "[AppSession/single sample session]"
    * a span string attribute "bugsnag.span.category" equals "app_session"
    * a span bool attribute "bugsnag.span.first_class" is true
    * a span integer attribute "bugsnag.system.memory.spaces.device.min" is greater than or equal to 0
    * a span integer attribute "bugsnag.system.memory.spaces.device.mean" is greater than or equal to 0
    * a span integer attribute "bugsnag.system.memory.spaces.device.max" is greater than or equal to 0
    * a span float attribute "bugsnag.system.cpu_min_total" is greater than or equal to 0.0
    * a span float attribute "bugsnag.system.cpu_mean_total" is greater than or equal to 0.0
    * a span float attribute "bugsnag.system.cpu_max_total" is greater than or equal to 0.0
    * a span integer attribute "bugsnag.system.memory.spaces.device.min" is less than or equal to span integer attribute "bugsnag.system.memory.spaces.device.max"
    * a span float attribute "bugsnag.system.cpu_min_total" is less than or equal to span float attribute "bugsnag.system.cpu_max_total"

Scenario: CPU disabled but memory enabled produces CPU absent and memory present
    Given I load scenario "AppSessionResourceUsageScenario"
    And I configure bugsnag "memoryMetrics" to "true"
    And I configure bugsnag "cpuMetrics" to "false"
    And I configure scenario "session_type" to "memory only session"
    And I configure scenario "span_duration" to "2.5"
    And I configure scenario "variant_name" to "MemoryOnly"
    And I start bugsnag
    And I run the loaded scenario
    And I wait to receive 1 trace
    Then a span field "name" equals "[AppSession/memory only session]"
    * a span string attribute "bugsnag.span.category" equals "app_session"
    * a span bool attribute "bugsnag.span.first_class" is true
    * a span integer attribute "bugsnag.system.memory.spaces.device.mean" is greater than or equal to 0
    * a span integer attribute "bugsnag.system.memory.spaces.device.min" is greater than or equal to 0
    * a span integer attribute "bugsnag.system.memory.spaces.device.max" is greater than or equal to 0
    * every span attribute "bugsnag.system.cpu_mean_total" does not exist
    * every span attribute "bugsnag.system.cpu_min_total" does not exist
    * every span attribute "bugsnag.system.cpu_max_total" does not exist

Scenario: Memory disabled but CPU enabled produces memory absent and CPU present
    Given I load scenario "AppSessionResourceUsageScenario"
    And I configure bugsnag "cpuMetrics" to "true"
    And I configure bugsnag "memoryMetrics" to "false"
    And I configure scenario "session_type" to "cpu only session"
    And I configure scenario "span_duration" to "2.5"
    And I configure scenario "work_duration" to "2.0"
    And I configure scenario "work_on_thread" to "main"
    And I configure scenario "variant_name" to "CPUOnly"
    And I start bugsnag
    And I run the loaded scenario
    And I wait to receive 1 trace
    Then a span field "name" equals "[AppSession/cpu only session]"
    * a span string attribute "bugsnag.span.category" equals "app_session"
    * a span bool attribute "bugsnag.span.first_class" is true
    * a span float attribute "bugsnag.system.cpu_mean_total" is greater than or equal to 0.0
    * a span float attribute "bugsnag.system.cpu_min_total" is greater than or equal to 0.0
    * a span float attribute "bugsnag.system.cpu_max_total" is greater than or equal to 0.0
    * every span attribute "bugsnag.system.memory.spaces.device.mean" does not exist
    * every span attribute "bugsnag.system.memory.spaces.device.min" does not exist
    * every span attribute "bugsnag.system.memory.spaces.device.max" does not exist

Scenario: App session span contains all CPU sub-metrics
    Given I load scenario "AppSessionResourceUsageScenario"
    And I configure bugsnag "cpuMetrics" to "true"
    And I configure bugsnag "memoryMetrics" to "false"
    And I configure scenario "session_type" to "CPUSubMetricsSession"
    And I configure scenario "span_duration" to "3.0"
    And I configure scenario "work_duration" to "2.5"
    And I configure scenario "work_on_thread" to "main"
    And I configure scenario "variant_name" to "CPUSubMetrics"
    And I start bugsnag
    And I run the loaded scenario
    And I wait to receive 1 trace
    Then a span field "name" equals "[AppSession/CPUSubMetricsSession]"
    * a span string attribute "bugsnag.span.category" equals "app_session"
    * a span bool attribute "bugsnag.span.first_class" is true
    * a span float attribute "bugsnag.system.cpu_mean_total" is greater than or equal to 0.0
    * a span float attribute "bugsnag.system.cpu_min_total" is greater than or equal to 0.0
    * a span float attribute "bugsnag.system.cpu_max_total" is greater than or equal to 0.0
    * a span float attribute "bugsnag.system.cpu_mean_main_thread" is greater than or equal to 0.0
    * a span float attribute "bugsnag.system.cpu_max_main_thread" is greater than or equal to 0.0
    * a span float attribute "bugsnag.system.cpu_mean_overhead" is greater than or equal to 0.0
    * a span float attribute "bugsnag.system.cpu_max_overhead" is greater than or equal to 0.0
    * a span float attribute "bugsnag.system.cpu_min_total" is less than or equal to span float attribute "bugsnag.system.cpu_mean_total"
    * a span float attribute "bugsnag.system.cpu_min_main_thread" is less than or equal to span float attribute "bugsnag.system.cpu_mean_main_thread"
    * a span float attribute "bugsnag.system.cpu_min_overhead" is less than or equal to span float attribute "bugsnag.system.cpu_mean_overhead"

Scenario: App session span includes ART memory metrics on Android
    Given I load scenario "AppSessionResourceUsageScenario"
    And I configure bugsnag "memoryMetrics" to "true"
    And I configure scenario "session_type" to "art metrics session"
    And I configure scenario "span_duration" to "2.0"
    And I configure scenario "variant_name" to "ART"
    And I start bugsnag
    And I run the loaded scenario
    And I wait to receive 1 trace
    And I dump all received spans
    Then a span field "name" equals "[AppSession/art metrics session]"
    * a span string attribute "bugsnag.span.category" equals "app_session"
    * a span bool attribute "bugsnag.span.first_class" is true
    * a span integer attribute "bugsnag.system.memory.spaces.art.mean" is greater than or equal to 0
    * a span integer attribute "bugsnag.system.memory.spaces.art.min" is greater than or equal to 0
    * a span integer attribute "bugsnag.system.memory.spaces.art.max" is greater than or equal to 0
    * a span integer attribute "bugsnag.system.memory.spaces.art.min" is less than or equal to span integer attribute "bugsnag.system.memory.spaces.art.mean"
    * a span integer attribute "bugsnag.system.memory.spaces.art.mean" is less than or equal to span integer attribute "bugsnag.system.memory.spaces.art.max"

Scenario: Two concurrent app sessions deliver independently with separate metrics
    Given I load scenario "AppSessionResourceUsageScenario"
    And I configure bugsnag "memoryMetrics" to "true"
    And I configure bugsnag "cpuMetrics" to "true"
    And I configure scenario "session_type" to "concurrent session A"
    And I configure scenario "concurrent_session_type" to "concurrent session B"
    And I configure scenario "span_duration" to "3.0"
    And I configure scenario "work_duration" to "2.0"
    And I configure scenario "work_on_thread" to "main"
    And I configure scenario "variant_name" to "Concurrent"
    And I start bugsnag
    And I run the loaded scenario
    And I wait to receive 2 traces
    Then a span field "name" equals "[AppSession/concurrent session A]"
    * a span string attribute "bugsnag.span.category" equals "app_session"
    * a span bool attribute "bugsnag.span.first_class" is true
    * a span integer attribute "bugsnag.system.memory.spaces.device.mean" is greater than or equal to 0
    * a span float attribute "bugsnag.system.cpu_mean_total" is greater than or equal to 0.0
    And a span field "name" equals "[AppSession/concurrent session B]"
    * a span string attribute "bugsnag.span.category" equals "app_session"
    * a span bool attribute "bugsnag.span.first_class" is true
    * a span integer attribute "bugsnag.system.memory.spaces.device.mean" is greater than or equal to 0
    * a span float attribute "bugsnag.system.cpu_mean_total" is greater than or equal to 0.0

Scenario: High CPU load during session produces higher cpu_max_total
    Given I load scenario "AppSessionResourceUsageScenario"
    And I configure bugsnag "cpuMetrics" to "true"
    And I configure bugsnag "memoryMetrics" to "false"
    And I configure scenario "session_type" to "high cpu load session"
    And I configure scenario "span_duration" to "3.0"
    And I configure scenario "work_duration" to "2.5"
    And I configure scenario "work_on_thread" to "main"
    And I configure scenario "variant_name" to "HighCPU"
    And I start bugsnag
    And I run the loaded scenario
    And I wait to receive 1 trace
    Then a span field "name" equals "[AppSession/high cpu load session]"
    * a span string attribute "bugsnag.span.category" equals "app_session"
    * a span bool attribute "bugsnag.span.first_class" is true
    * a span float attribute "bugsnag.system.cpu_mean_total" is greater than or equal to 0.5

Scenario: Memory allocation during session produces higher memory max than min
    Given I load scenario "AppSessionResourceUsageScenario"
    And I configure bugsnag "memoryMetrics" to "true"
    And I configure bugsnag "cpuMetrics" to "false"
    And I configure scenario "session_type" to "memory allocation session"
    And I configure scenario "span_duration" to "3.0"
    And I configure scenario "variant_name" to "MemoryAllocation"
    And I start bugsnag
    And I run the loaded scenario
    And I wait to receive 1 trace
    Then a span field "name" equals "[AppSession/memory allocation session]"
    * a span string attribute "bugsnag.span.category" equals "app_session"
    * a span bool attribute "bugsnag.span.first_class" is true
    * a span integer attribute "bugsnag.system.memory.spaces.device.min" is greater than or equal to 0
    * a span integer attribute "bugsnag.system.memory.spaces.device.max" is greater than or equal to 0
    * a span integer attribute "bugsnag.system.memory.spaces.device.min" is less than or equal to span integer attribute "bugsnag.system.memory.spaces.device.max"

Scenario: App session span does not parent other spans
    Given I load scenario "AppSessionResourceUsageScenario"
    And I configure bugsnag "memoryMetrics" to "true"
    And I configure bugsnag "cpuMetrics" to "true"
    And I configure scenario "session_type" to "parent check session"
    And I configure scenario "span_duration" to "2.0"
    And I configure scenario "create_child_span" to "true"
    And I configure scenario "work_duration" to "1.0"
    And I configure scenario "work_on_thread" to "main"
    And I configure scenario "variant_name" to "ParentCheck"
    And I start bugsnag
    And I run the loaded scenario
    And I wait to receive 2 traces
    Then a span field "name" equals "[AppSession/parent check session]"
    * a span string attribute "bugsnag.span.category" equals "app_session"
    * a span bool attribute "bugsnag.span.first_class" is true
    And a span field "name" equals "ChildSpanInsideSession"
    * a span string attribute "bugsnag.span.category" equals "custom"
    * a span field "parentSpanId" is empty

Scenario: Very short app session span produces valid metrics
    Given I load scenario "AppSessionResourceUsageScenario"
    And I configure bugsnag "memoryMetrics" to "true"
    And I configure bugsnag "cpuMetrics" to "true"
    And I configure scenario "session_type" to "short session"
    And I configure scenario "span_duration" to "0.1"
    And I configure scenario "work_duration" to "0.05"
    And I configure scenario "work_on_thread" to "main"
    And I configure scenario "variant_name" to "Short"
    And I start bugsnag
    And I run the loaded scenario
    And I wait for 1 second
    And I wait to receive 1 trace
    Then a span field "name" equals "[AppSession/short session]"
    * a span string attribute "bugsnag.span.category" equals "app_session"
    * a span bool attribute "bugsnag.span.first_class" is true
    * a span integer attribute "bugsnag.system.memory.spaces.device.min" is greater than or equal to 0
    * a span float attribute "bugsnag.system.cpu_min_total" is greater than or equal to 0.0
