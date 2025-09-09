Feature: Device Metrics

  Scenario Outline:
    When I run "SpanOpenCloseBenchmark" configured as <options>
    * I wait for 30 seconds
    * I wait to receive at least 1 metrics
    * I discard the oldest metric
    * I relaunch the app after shutdown

    Examples:
      | options                          |
      | ""                               |
      | "rendering"                      |
      | "cpu"                            |
      | "memory"                         |
      | "NamedSpan"                      |
      | "rendering cpu memory"           |
      | "rendering cpu memory NamedSpan" |
