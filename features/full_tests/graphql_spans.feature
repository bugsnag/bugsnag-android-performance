Feature: GraphQL Spans

  # Scenario 1 - Passed
  Scenario Outline: GraphQL detected via <detection_method> produces correct span with full attributes
    Given I run "GraphQlContentTypeScenario" configured as "http://{MAZE_ADDRESS}/traces|||<content_type>|||<body>"
    And I wait to receive at least 1 span
    * a span field "kind" equals 3
    * a span field "name" matches the regex "^\[GraphQL\] \[.*/traces\] query:GetCountries$"
    * a span string attribute "bugsnag.span.category" equals "graphql"
    * a span string attribute "http.url" matches the regex "^http://.*/traces$"
    * a span string attribute "http.method" equals "POST"
    * a span integer attribute "http.status_code" is greater than 0

    Examples:
      | detection_method                 | content_type        | body                                            |
      | Content-Type application/graphql | application/graphql | query GetCountries { countries { code name } } |

  # Scenario 2 - Passed
  Scenario Outline: Operation type "<op_type>" correctly extracted with name priority "<priority>"
    Given I run "GraphQlContentTypeScenario" configured as "http://{MAZE_ADDRESS}/graphql|||application/json|||<body>"
    And I wait to receive at least 1 span
    * a span field "name" matches the regex "^\[GraphQL\] \[.*/graphql\] <op_type>:<expected_name>$"

    Examples:
      | op_type | priority                 | body                                                                    | expected_name |
      | query   | operationName field (P1) | {\"query\": \"query GetUser { user { id } }\", \"operationName\": \"GetUser\"} | GetUser       |

  # Scenario 4 - Passed
  Scenario Outline: Non-GraphQL request "<case>" retains network category
    Given I run "GraphQlContentTypeScenario" configured as "http://{MAZE_ADDRESS}/rest/users|||<content_type>|||<body>"
    And I wait to receive at least 1 span
    * a span string attribute "bugsnag.span.category" equals "network"
    * a span field "name" equals "[HTTP/POST]"

    Examples:
      | case               | content_type     | body                                 |
      | Standard REST POST | application/json | {\"userId\": \"123\", \"action\": \"get\"} |

  # Scenario 5 - Passed
  Scenario Outline: POST with <body_type> body does not crash and falls back to network
    Given I run "GraphQlContentTypeScenario" configured as "http://{MAZE_ADDRESS}/api/data|||application/json|||<body>"
    And I wait to receive at least 1 span
    * a span string attribute "bugsnag.span.category" equals "network"
    * a span field "name" equals "[HTTP/POST]"

    Examples:
      | body_type         | body                  |
      | empty string      |                       |
      | malformed JSON    | {invalid json content |
      | empty JSON object | {}                    |

  # Scenario 6 - Passed
  Scenario Outline: Operation name "<name_type>" is handled without crash or data loss
    Given I run "GraphQlContentTypeScenario" configured as "http://{MAZE_ADDRESS}/graphql|||application/json|||<body>"
    And I wait to receive at least 1 span
    * a span field "name" matches the regex "^\[GraphQL\] \[.*/graphql\] query:<expected_name>$"

    Examples:
      | name_type                     | body                                                                                                                                                                                                    | expected_name                                                                                 |
      | Very long name (128+ chars)   | {\"query\": \"query AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA { user { id } }\", \"operationName\": \"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\"} | AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA |
      | Underscore and version suffix | {\"query\": \"query Get_User_Profile_V2 { user { id } }\", \"operationName\": \"Get_User_Profile_V2\"}                                                                                                            | Get_User_Profile_V2                                                                           |
      | Numeric suffix                | {\"query\": \"query GetUser123 { user { id } }\", \"operationName\": \"GetUser123\"}                                                                                                                              | GetUser123                                                                                    |

  # Scenario 8 - passed
  Scenario: GET request to /graphql with query params does not crash
    Given I run "OkhttpSpanScenario" configured as "http://{MAZE_ADDRESS}/graphql?query={user{id}}&operationName=GetUser"
    And I wait to receive at least 1 span
    * a span field "kind" equals 3
    * a span string attribute "bugsnag.span.category" equals "graphql"
    * a span string attribute "http.method" equals "GET"
    * a span field "name" matches the regex "^\[GraphQL\] \[.*/graphql\] query$"

  # Scenario 9 - passed
  Scenario: Multiple GraphQL operations create distinct span names and coexist with network spans
    Given I run "MultipleGraphQlScenario" configured as "http://{MAZE_ADDRESS}"
    And I wait to receive 4 spans
    Then a span field "name" matches the regex "^\[GraphQL\] \[.*/graphql\] query:GetUser$"
    And a span field "name" matches the regex "^\[GraphQL\] \[.*/graphql\] mutation:CreatePost$"
    And a span field "name" matches the regex "^\[HTTP/GET\]$"
    And a span string attribute "bugsnag.span.category" equals "graphql"
    And a span string attribute "bugsnag.span.category" equals "network"

  # Scenario 10 - passed
  Scenario: GraphQL span payload contains only HTTP attributes and category - no GraphQL-specific metadata
    Given I run "GraphQlContentTypeScenario" configured as "http://{MAZE_ADDRESS}/graphql|||application/graphql|||query { user { id secret } }|||true"
    And I wait to receive at least 1 span
    * a span string attribute "bugsnag.span.category" equals "graphql"
    * a span bool attribute "bugsnag.span.first_class" is true
    * a span string attribute "http.method" equals "POST"
    * a span string attribute "http.url" matches the regex "^http://.*/graphql$"
    * a span integer attribute "http.status_code" is greater than 0
    * every span attribute "graphql.operation.name" does not exist
    * every span attribute "graphql.operation.type" does not exist
    * every span attribute "graphql.document" does not exist
    * every span attribute "graphql.variables" does not exist

  # Scenario 11 - passed
  Scenario: GraphQL span with explicit first_class=false is not aggregated into span groups
    Given I run "GraphQlContentTypeScenario" configured as "http://{MAZE_ADDRESS}/traces|||application/graphql|||query GetCountries { countries { code name } }|||false"
    And I wait to receive at least 1 span
    * a span field "kind" equals 3
    * a span string attribute "bugsnag.span.category" equals "graphql"
    * a span bool attribute "bugsnag.span.first_class" is false

  # Scenario 13 - passed
  Scenario: Android SDK produces GraphQL span via supported GraphQL client library
    Given I run "ApolloScenario" configured as "http://{MAZE_ADDRESS}/graphql"
    And I wait to receive at least 1 span
    * a span field "kind" equals 3
    * a span string attribute "bugsnag.span.category" equals "graphql"
    * a span field "name" matches the regex "^\[GraphQL\] \[.*/graphql\] query:TestQuery$"
    * every span attribute "graphql.document" does not exist

  # Scenario 15 - passed
  Scenario: Multiple identical GraphQL operations produce spans with consistent name for pipeline grouping
    Given I run "IdenticalGraphQlScenario" configured as "http://{MAZE_ADDRESS}"
    And I wait to receive 3 spans
    * every span field "name" matches the regex "^\[GraphQL\] \[.*/graphql\] query:GetUser$"
    * every span string attribute "bugsnag.span.category" equals "graphql"
    * every span field "spanId" matches the regex "^[0-9a-f]{16}$"
    * every span field "traceId" matches the regex "^[0-9a-f]{32}$"

  # Scenario 16
  Scenario Outline: GraphQL response with <error_type> sets span status to <expected_status>
    Given I set the HTTP status code for the next request to <http_status>
    And I set the HTTP response body for the next request to "<body>"
    And I run "GraphQlContentTypeScenario" configured as "http://{MAZE_ADDRESS}/graphql|||application/json|||{\"query\": \"query GetUser { user { id } }\", \"operationName\": \"GetUser\"}"
    And I wait to receive at least 1 span
    * a span field "name" matches the regex "^\[GraphQL\] \[.*/graphql\] query:GetUser$"
    * a span string attribute "bugsnag.span.category" equals "graphql"
    * a span integer attribute "http.status_code" equals <http_status>
    * a span field "status.code" equals <expected_status_code>

    Examples:
      | error_type                          | http_status | body                                                                        | expected_status   | expected_status_code |
      | HTTP 200 with errors array          | 200         | {\"data\": null, \"errors\": [{\"message\": \"User not found\"}]}                 | STATUS_CODE_ERROR | 2                    |
      | HTTP 200 with partial data + errors | 200         | {\"data": {\"user\": {\"id\": \"1\"}}, \"errors\": [{\"message\": \"Field deprecated\"}]} | STATUS_CODE_ERROR | 2                    |
      | HTTP 200 success (no errors)        | 200         | {"data": {\"user\": {\"id\": \"1\", \"name\": \"John\"}}}                            | STATUS_CODE_OK    | 1                    |
      | HTTP 200 with empty errors array    | 200         | {"data": {\"user\": {\"id\": \"1\"}}, \"errors\": []}                               | STATUS_CODE_OK    | 1                    |
      | HTTP 500 transport error            | 500         | {}                                                                          | STATUS_CODE_ERROR | 2                    |
      | HTTP 401 unauthorized               | 401         | {}                                                                          | STATUS_CODE_ERROR | 2                    |
