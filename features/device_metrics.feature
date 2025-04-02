Feature: Device Metrics

  Scenario: CPU & Memory Test
    When I run "DeviceMetricsScenario" configured as "all"
    * I wait to receive a trace

    # Check CPU Metrics on first class spans
    * the "FirstClass" span has double attribute named "bugsnag.system.cpu_mean_total"
    * the "FirstClass" span has double attribute named "bugsnag.system.cpu_mean_main_thread"
    * the "FirstClass" span has double attribute named "bugsnag.system.cpu_mean_overhead"

    * the "FirstClass" span has array attribute named "bugsnag.system.cpu_measures_total"
    * the "FirstClass" span has array attribute named "bugsnag.system.cpu_measures_main_thread"
    * the "FirstClass" span has array attribute named "bugsnag.system.cpu_measures_overhead"
    * the "FirstClass" span has array attribute named "bugsnag.system.cpu_measures_timestamps"

    # Check Memory Metrics on first class spans
    * the "FirstClass" span has int attribute named "bugsnag.system.memory.spaces.art.size"
    * the "FirstClass" span has int attribute named "bugsnag.device.physical_device_memory"
    * the "FirstClass" span has int attribute named "bugsnag.system.memory.spaces.device.size"
    * the "FirstClass" span has int attribute named "bugsnag.system.memory.spaces.device.mean"
    * the "FirstClass" span has int attribute named "bugsnag.system.memory.spaces.art.mean"

    * the "FirstClass" span has array attribute named "bugsnag.system.memory.spaces.space_names"
    * the "FirstClass" span has array attribute named "bugsnag.system.memory.spaces.device.used"
    * the "FirstClass" span has array attribute named "bugsnag.system.memory.spaces.art.used"
    * the "FirstClass" span has array attribute named "bugsnag.system.memory.timestamps"

    # Check that no metrics are reported on the "No Metrics" span
    * the "No Metrics" span has no "bugsnag.system.cpu_mean_total" attribute
    * the "No Metrics" span has no "bugsnag.system.cpu_mean_main_thread" attribute
    * the "No Metrics" span has no "bugsnag.system.cpu_mean_overhead" attribute
    * the "No Metrics" span has no "bugsnag.system.cpu_measures_total" attribute
    * the "No Metrics" span has no "bugsnag.system.cpu_measures_main_thread" attribute
    * the "No Metrics" span has no "bugsnag.system.cpu_measures_overhead" attribute
    * the "No Metrics" span has no "bugsnag.system.cpu_measures_timestamps" attribute

    * the "No Metrics" span has no "bugsnag.system.memory.spaces.art.size" attribute
    * the "No Metrics" span has no "bugsnag.device.physical_device_memory" attribute
    * the "No Metrics" span has no "bugsnag.system.memory.spaces.device.size" attribute
    * the "No Metrics" span has no "bugsnag.system.memory.spaces.device.mean" attribute
    * the "No Metrics" span has no "bugsnag.system.memory.spaces.art.mean" attribute
    * the "No Metrics" span has no "bugsnag.system.memory.spaces.space_names" attribute
    * the "No Metrics" span has no "bugsnag.system.memory.spaces.device.used" attribute
    * the "No Metrics" span has no "bugsnag.system.memory.spaces.art.used" attribute
    * the "No Metrics" span has no "bugsnag.system.memory.timestamps" attribute
    
    # Check that no metrics are reported on the "Not FirstClass" span
    * the "Not FirstClass" span has no "bugsnag.system.cpu_mean_total" attribute
    * the "Not FirstClass" span has no "bugsnag.system.cpu_mean_main_thread" attribute
    * the "Not FirstClass" span has no "bugsnag.system.cpu_mean_overhead" attribute
    * the "Not FirstClass" span has no "bugsnag.system.cpu_measures_total" attribute
    * the "Not FirstClass" span has no "bugsnag.system.cpu_measures_main_thread" attribute
    * the "Not FirstClass" span has no "bugsnag.system.cpu_measures_overhead" attribute
    * the "Not FirstClass" span has no "bugsnag.system.cpu_measures_timestamps" attribute

    * the "Not FirstClass" span has no "bugsnag.system.memory.spaces.art.size" attribute
    * the "Not FirstClass" span has no "bugsnag.device.physical_device_memory" attribute
    * the "Not FirstClass" span has no "bugsnag.system.memory.spaces.device.size" attribute
    * the "Not FirstClass" span has no "bugsnag.system.memory.spaces.device.mean" attribute
    * the "Not FirstClass" span has no "bugsnag.system.memory.spaces.art.mean" attribute
    * the "Not FirstClass" span has no "bugsnag.system.memory.spaces.space_names" attribute
    * the "Not FirstClass" span has no "bugsnag.system.memory.spaces.device.used" attribute
    * the "Not FirstClass" span has no "bugsnag.system.memory.spaces.art.used" attribute
    * the "Not FirstClass" span has no "bugsnag.system.memory.timestamps" attribute
    
    # Check that only CPU Metrics are recorded on the "CPU Metrics Only" span
    * the "CPU Metrics Only" span has double attribute named "bugsnag.system.cpu_mean_total"
    * the "CPU Metrics Only" span has double attribute named "bugsnag.system.cpu_mean_main_thread"
    * the "CPU Metrics Only" span has double attribute named "bugsnag.system.cpu_mean_overhead"

    * the "CPU Metrics Only" span has array attribute named "bugsnag.system.cpu_measures_total"
    * the "CPU Metrics Only" span has array attribute named "bugsnag.system.cpu_measures_main_thread"
    * the "CPU Metrics Only" span has array attribute named "bugsnag.system.cpu_measures_overhead"
    * the "CPU Metrics Only" span has array attribute named "bugsnag.system.cpu_measures_timestamps"

    * the "CPU Metrics Only" span has no "bugsnag.system.memory.spaces.art.size" attribute
    * the "CPU Metrics Only" span has no "bugsnag.device.physical_device_memory" attribute
    * the "CPU Metrics Only" span has no "bugsnag.system.memory.spaces.device.size" attribute
    * the "CPU Metrics Only" span has no "bugsnag.system.memory.spaces.device.mean" attribute
    * the "CPU Metrics Only" span has no "bugsnag.system.memory.spaces.art.mean" attribute
    * the "CPU Metrics Only" span has no "bugsnag.system.memory.spaces.space_names" attribute
    * the "CPU Metrics Only" span has no "bugsnag.system.memory.spaces.device.used" attribute
    * the "CPU Metrics Only" span has no "bugsnag.system.memory.spaces.art.used" attribute
    * the "CPU Metrics Only" span has no "bugsnag.system.memory.timestamps" attribute

    # Check that only Memory Metrics are recorded on the "Memory Metrics Only" span
    * the "Memory Metrics Only" span has no "bugsnag.system.cpu_mean_total" attribute
    * the "Memory Metrics Only" span has no "bugsnag.system.cpu_mean_main_thread" attribute
    * the "Memory Metrics Only" span has no "bugsnag.system.cpu_mean_overhead" attribute
    * the "Memory Metrics Only" span has no "bugsnag.system.cpu_measures_total" attribute
    * the "Memory Metrics Only" span has no "bugsnag.system.cpu_measures_main_thread" attribute
    * the "Memory Metrics Only" span has no "bugsnag.system.cpu_measures_overhead" attribute
    * the "Memory Metrics Only" span has no "bugsnag.system.cpu_measures_timestamps" attribute

    * the "Memory Metrics Only" span has int attribute named "bugsnag.system.memory.spaces.art.size"
    * the "Memory Metrics Only" span has int attribute named "bugsnag.device.physical_device_memory"
    * the "Memory Metrics Only" span has int attribute named "bugsnag.system.memory.spaces.device.size"
    * the "Memory Metrics Only" span has int attribute named "bugsnag.system.memory.spaces.device.mean"
    * the "Memory Metrics Only" span has int attribute named "bugsnag.system.memory.spaces.art.mean"

    * the "Memory Metrics Only" span has array attribute named "bugsnag.system.memory.spaces.space_names"
    * the "Memory Metrics Only" span has array attribute named "bugsnag.system.memory.spaces.device.used"
    * the "Memory Metrics Only" span has array attribute named "bugsnag.system.memory.spaces.art.used"
    * the "Memory Metrics Only" span has array attribute named "bugsnag.system.memory.timestamps"

  Scenario: Configured for CPU Metrics only
    When I run "DeviceMetricsScenario" configured as "cpu"
    * I wait to receive a trace

    # Check CPU Metrics on first class spans
    * the "FirstClass" span has double attribute named "bugsnag.system.cpu_mean_total"
    * the "FirstClass" span has double attribute named "bugsnag.system.cpu_mean_main_thread"
    * the "FirstClass" span has double attribute named "bugsnag.system.cpu_mean_overhead"

    * the "FirstClass" span has array attribute named "bugsnag.system.cpu_measures_total"
    * the "FirstClass" span has array attribute named "bugsnag.system.cpu_measures_main_thread"
    * the "FirstClass" span has array attribute named "bugsnag.system.cpu_measures_overhead"
    * the "FirstClass" span has array attribute named "bugsnag.system.cpu_measures_timestamps"

    # Check the there are no Memory Metrics on first class span
    * the "FirstClass" span has no "bugsnag.system.memory.spaces.art.size" attribute
    * the "FirstClass" span has no "bugsnag.device.physical_device_memory" attribute
    * the "FirstClass" span has no "bugsnag.system.memory.spaces.device.size" attribute
    * the "FirstClass" span has no "bugsnag.system.memory.spaces.device.mean" attribute
    * the "FirstClass" span has no "bugsnag.system.memory.spaces.art.mean" attribute
    * the "FirstClass" span has no "bugsnag.system.memory.spaces.space_names" attribute
    * the "FirstClass" span has no "bugsnag.system.memory.spaces.device.used" attribute
    * the "FirstClass" span has no "bugsnag.system.memory.spaces.art.used" attribute
    * the "FirstClass" span has no "bugsnag.system.memory.timestamps" attribute

    # Check that no metrics are reported on the "No Metrics" span
    * the "No Metrics" span has no "bugsnag.system.cpu_mean_total" attribute
    * the "No Metrics" span has no "bugsnag.system.cpu_mean_main_thread" attribute
    * the "No Metrics" span has no "bugsnag.system.cpu_mean_overhead" attribute
    * the "No Metrics" span has no "bugsnag.system.cpu_measures_total" attribute
    * the "No Metrics" span has no "bugsnag.system.cpu_measures_main_thread" attribute
    * the "No Metrics" span has no "bugsnag.system.cpu_measures_overhead" attribute
    * the "No Metrics" span has no "bugsnag.system.cpu_measures_timestamps" attribute

    * the "No Metrics" span has no "bugsnag.system.memory.spaces.art.size" attribute
    * the "No Metrics" span has no "bugsnag.device.physical_device_memory" attribute
    * the "No Metrics" span has no "bugsnag.system.memory.spaces.device.size" attribute
    * the "No Metrics" span has no "bugsnag.system.memory.spaces.device.mean" attribute
    * the "No Metrics" span has no "bugsnag.system.memory.spaces.art.mean" attribute
    * the "No Metrics" span has no "bugsnag.system.memory.spaces.space_names" attribute
    * the "No Metrics" span has no "bugsnag.system.memory.spaces.device.used" attribute
    * the "No Metrics" span has no "bugsnag.system.memory.spaces.art.used" attribute
    * the "No Metrics" span has no "bugsnag.system.memory.timestamps" attribute

    # Check that no metrics are reported on the "Not FirstClass" span
    * the "Not FirstClass" span has no "bugsnag.system.cpu_mean_total" attribute
    * the "Not FirstClass" span has no "bugsnag.system.cpu_mean_main_thread" attribute
    * the "Not FirstClass" span has no "bugsnag.system.cpu_mean_overhead" attribute
    * the "Not FirstClass" span has no "bugsnag.system.cpu_measures_total" attribute
    * the "Not FirstClass" span has no "bugsnag.system.cpu_measures_main_thread" attribute
    * the "Not FirstClass" span has no "bugsnag.system.cpu_measures_overhead" attribute
    * the "Not FirstClass" span has no "bugsnag.system.cpu_measures_timestamps" attribute

    * the "Not FirstClass" span has no "bugsnag.system.memory.spaces.art.size" attribute
    * the "Not FirstClass" span has no "bugsnag.device.physical_device_memory" attribute
    * the "Not FirstClass" span has no "bugsnag.system.memory.spaces.device.size" attribute
    * the "Not FirstClass" span has no "bugsnag.system.memory.spaces.device.mean" attribute
    * the "Not FirstClass" span has no "bugsnag.system.memory.spaces.art.mean" attribute
    * the "Not FirstClass" span has no "bugsnag.system.memory.spaces.space_names" attribute
    * the "Not FirstClass" span has no "bugsnag.system.memory.spaces.device.used" attribute
    * the "Not FirstClass" span has no "bugsnag.system.memory.spaces.art.used" attribute
    * the "Not FirstClass" span has no "bugsnag.system.memory.timestamps" attribute

    # Check that only CPU Metrics are recorded on the "CPU Metrics Only" span
    * the "CPU Metrics Only" span has double attribute named "bugsnag.system.cpu_mean_total"
    * the "CPU Metrics Only" span has double attribute named "bugsnag.system.cpu_mean_main_thread"
    * the "CPU Metrics Only" span has double attribute named "bugsnag.system.cpu_mean_overhead"

    * the "CPU Metrics Only" span has array attribute named "bugsnag.system.cpu_measures_total"
    * the "CPU Metrics Only" span has array attribute named "bugsnag.system.cpu_measures_main_thread"
    * the "CPU Metrics Only" span has array attribute named "bugsnag.system.cpu_measures_overhead"
    * the "CPU Metrics Only" span has array attribute named "bugsnag.system.cpu_measures_timestamps"

    * the "CPU Metrics Only" span has no "bugsnag.system.memory.spaces.art.size" attribute
    * the "CPU Metrics Only" span has no "bugsnag.device.physical_device_memory" attribute
    * the "CPU Metrics Only" span has no "bugsnag.system.memory.spaces.device.size" attribute
    * the "CPU Metrics Only" span has no "bugsnag.system.memory.spaces.device.mean" attribute
    * the "CPU Metrics Only" span has no "bugsnag.system.memory.spaces.art.mean" attribute
    * the "CPU Metrics Only" span has no "bugsnag.system.memory.spaces.space_names" attribute
    * the "CPU Metrics Only" span has no "bugsnag.system.memory.spaces.device.used" attribute
    * the "CPU Metrics Only" span has no "bugsnag.system.memory.spaces.art.used" attribute
    * the "CPU Metrics Only" span has no "bugsnag.system.memory.timestamps" attribute

    # Check that no metrics are recorded on the "Memory Metrics Only" span for this case
    * the "Memory Metrics Only" span has no "bugsnag.system.cpu_mean_total" attribute
    * the "Memory Metrics Only" span has no "bugsnag.system.cpu_mean_main_thread" attribute
    * the "Memory Metrics Only" span has no "bugsnag.system.cpu_mean_overhead" attribute
    * the "Memory Metrics Only" span has no "bugsnag.system.cpu_measures_total" attribute
    * the "Memory Metrics Only" span has no "bugsnag.system.cpu_measures_main_thread" attribute
    * the "Memory Metrics Only" span has no "bugsnag.system.cpu_measures_overhead" attribute
    * the "Memory Metrics Only" span has no "bugsnag.system.cpu_measures_timestamps" attribute

    * the "Memory Metrics Only" span has no "bugsnag.system.memory.spaces.art.size" attribute
    * the "Memory Metrics Only" span has no "bugsnag.device.physical_device_memory" attribute
    * the "Memory Metrics Only" span has no "bugsnag.system.memory.spaces.device.size" attribute
    * the "Memory Metrics Only" span has no "bugsnag.system.memory.spaces.device.mean" attribute
    * the "Memory Metrics Only" span has no "bugsnag.system.memory.spaces.art.mean" attribute
    * the "Memory Metrics Only" span has no "bugsnag.system.memory.spaces.space_names" attribute
    * the "Memory Metrics Only" span has no "bugsnag.system.memory.spaces.device.used" attribute
    * the "Memory Metrics Only" span has no "bugsnag.system.memory.spaces.art.used" attribute
    * the "Memory Metrics Only" span has no "bugsnag.system.memory.timestamps" attribute

  Scenario: Configured for Memory Metrics only
    When I run "DeviceMetricsScenario" configured as "memory"
    * I wait to receive a trace

    # Check there are no CPU Metrics on first class spans
    * the "FirstClass" span has no "bugsnag.system.cpu_mean_total" attribute
    * the "FirstClass" span has no "bugsnag.system.cpu_mean_main_thread" attribute
    * the "FirstClass" span has no "bugsnag.system.cpu_mean_overhead" attribute
    * the "FirstClass" span has no "bugsnag.system.cpu_measures_total" attribute
    * the "FirstClass" span has no "bugsnag.system.cpu_measures_main_thread" attribute
    * the "FirstClass" span has no "bugsnag.system.cpu_measures_overhead" attribute
    * the "FirstClass" span has no "bugsnag.system.cpu_measures_timestamps" attribute

    # Check Memory Metrics on first class spans
    * the "FirstClass" span has int attribute named "bugsnag.system.memory.spaces.art.size"
    * the "FirstClass" span has int attribute named "bugsnag.device.physical_device_memory"
    * the "FirstClass" span has int attribute named "bugsnag.system.memory.spaces.device.size"
    * the "FirstClass" span has int attribute named "bugsnag.system.memory.spaces.device.mean"
    * the "FirstClass" span has int attribute named "bugsnag.system.memory.spaces.art.mean"

    * the "FirstClass" span has array attribute named "bugsnag.system.memory.spaces.space_names"
    * the "FirstClass" span has array attribute named "bugsnag.system.memory.spaces.device.used"
    * the "FirstClass" span has array attribute named "bugsnag.system.memory.spaces.art.used"
    * the "FirstClass" span has array attribute named "bugsnag.system.memory.timestamps"
    
    # Check that no metrics are reported on the "No Metrics" span
    * the "No Metrics" span has no "bugsnag.system.cpu_mean_total" attribute
    * the "No Metrics" span has no "bugsnag.system.cpu_mean_main_thread" attribute
    * the "No Metrics" span has no "bugsnag.system.cpu_mean_overhead" attribute
    * the "No Metrics" span has no "bugsnag.system.cpu_measures_total" attribute
    * the "No Metrics" span has no "bugsnag.system.cpu_measures_main_thread" attribute
    * the "No Metrics" span has no "bugsnag.system.cpu_measures_overhead" attribute
    * the "No Metrics" span has no "bugsnag.system.cpu_measures_timestamps" attribute

    * the "No Metrics" span has no "bugsnag.system.memory.spaces.art.size" attribute
    * the "No Metrics" span has no "bugsnag.device.physical_device_memory" attribute
    * the "No Metrics" span has no "bugsnag.system.memory.spaces.device.size" attribute
    * the "No Metrics" span has no "bugsnag.system.memory.spaces.device.mean" attribute
    * the "No Metrics" span has no "bugsnag.system.memory.spaces.art.mean" attribute
    * the "No Metrics" span has no "bugsnag.system.memory.spaces.space_names" attribute
    * the "No Metrics" span has no "bugsnag.system.memory.spaces.device.used" attribute
    * the "No Metrics" span has no "bugsnag.system.memory.spaces.art.used" attribute
    * the "No Metrics" span has no "bugsnag.system.memory.timestamps" attribute

    # Check that no metrics are reported on the "Not FirstClass" span
    * the "Not FirstClass" span has no "bugsnag.system.cpu_mean_total" attribute
    * the "Not FirstClass" span has no "bugsnag.system.cpu_mean_main_thread" attribute
    * the "Not FirstClass" span has no "bugsnag.system.cpu_mean_overhead" attribute
    * the "Not FirstClass" span has no "bugsnag.system.cpu_measures_total" attribute
    * the "Not FirstClass" span has no "bugsnag.system.cpu_measures_main_thread" attribute
    * the "Not FirstClass" span has no "bugsnag.system.cpu_measures_overhead" attribute
    * the "Not FirstClass" span has no "bugsnag.system.cpu_measures_timestamps" attribute

    * the "Not FirstClass" span has no "bugsnag.system.memory.spaces.art.size" attribute
    * the "Not FirstClass" span has no "bugsnag.device.physical_device_memory" attribute
    * the "Not FirstClass" span has no "bugsnag.system.memory.spaces.device.size" attribute
    * the "Not FirstClass" span has no "bugsnag.system.memory.spaces.device.mean" attribute
    * the "Not FirstClass" span has no "bugsnag.system.memory.spaces.art.mean" attribute
    * the "Not FirstClass" span has no "bugsnag.system.memory.spaces.space_names" attribute
    * the "Not FirstClass" span has no "bugsnag.system.memory.spaces.device.used" attribute
    * the "Not FirstClass" span has no "bugsnag.system.memory.spaces.art.used" attribute
    * the "Not FirstClass" span has no "bugsnag.system.memory.timestamps" attribute

    # Check that there are no CPU Metrics are recorded on the "CPU Metrics Only" span (not gathered)
    * the "CPU Metrics Only" span has no "bugsnag.system.cpu_mean_total" attribute
    * the "CPU Metrics Only" span has no "bugsnag.system.cpu_mean_main_thread" attribute
    * the "CPU Metrics Only" span has no "bugsnag.system.cpu_mean_overhead" attribute
    * the "CPU Metrics Only" span has no "bugsnag.system.cpu_measures_total" attribute
    * the "CPU Metrics Only" span has no "bugsnag.system.cpu_measures_main_thread" attribute
    * the "CPU Metrics Only" span has no "bugsnag.system.cpu_measures_overhead" attribute
    * the "CPU Metrics Only" span has no "bugsnag.system.cpu_measures_timestamps" attribute

    * the "CPU Metrics Only" span has no "bugsnag.system.memory.spaces.art.size" attribute
    * the "CPU Metrics Only" span has no "bugsnag.device.physical_device_memory" attribute
    * the "CPU Metrics Only" span has no "bugsnag.system.memory.spaces.device.size" attribute
    * the "CPU Metrics Only" span has no "bugsnag.system.memory.spaces.device.mean" attribute
    * the "CPU Metrics Only" span has no "bugsnag.system.memory.spaces.art.mean" attribute
    * the "CPU Metrics Only" span has no "bugsnag.system.memory.spaces.space_names" attribute
    * the "CPU Metrics Only" span has no "bugsnag.system.memory.spaces.device.used" attribute
    * the "CPU Metrics Only" span has no "bugsnag.system.memory.spaces.art.used" attribute
    * the "CPU Metrics Only" span has no "bugsnag.system.memory.timestamps" attribute

    # Check that only Memory Metrics are recorded on the "Memory Metrics Only" span
    * the "Memory Metrics Only" span has no "bugsnag.system.cpu_mean_total" attribute
    * the "Memory Metrics Only" span has no "bugsnag.system.cpu_mean_main_thread" attribute
    * the "Memory Metrics Only" span has no "bugsnag.system.cpu_mean_overhead" attribute
    * the "Memory Metrics Only" span has no "bugsnag.system.cpu_measures_total" attribute
    * the "Memory Metrics Only" span has no "bugsnag.system.cpu_measures_main_thread" attribute
    * the "Memory Metrics Only" span has no "bugsnag.system.cpu_measures_overhead" attribute
    * the "Memory Metrics Only" span has no "bugsnag.system.cpu_measures_timestamps" attribute

    * the "Memory Metrics Only" span has int attribute named "bugsnag.system.memory.spaces.art.size"
    * the "Memory Metrics Only" span has int attribute named "bugsnag.device.physical_device_memory"
    * the "Memory Metrics Only" span has int attribute named "bugsnag.system.memory.spaces.device.size"
    * the "Memory Metrics Only" span has int attribute named "bugsnag.system.memory.spaces.device.mean"
    * the "Memory Metrics Only" span has int attribute named "bugsnag.system.memory.spaces.art.mean"

    * the "Memory Metrics Only" span has array attribute named "bugsnag.system.memory.spaces.space_names"
    * the "Memory Metrics Only" span has array attribute named "bugsnag.system.memory.spaces.device.used"
    * the "Memory Metrics Only" span has array attribute named "bugsnag.system.memory.spaces.art.used"
    * the "Memory Metrics Only" span has array attribute named "bugsnag.system.memory.timestamps"