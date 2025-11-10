Feature: Device Metrics

  Scenario Outline: Benchmarking (<options>)
    When I run "SpanOpenCloseBenchmark" configured as "<options>"
    * I wait to receive at least 1 metric
    * I discard the oldest metric

    Examples:
      | options                        |
      |                                |
      | rendering                      |
      | cpu                            |
      | memory                         |
      | NamedSpan                      |
      | rendering cpu memory           |
      | rendering cpu memory NamedSpan |
