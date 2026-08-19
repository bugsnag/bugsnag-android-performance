Feature: GraphQL Spans

  # Scenario 1
  Scenario Outline: GraphQL detected via <detection_method> produces correct span with full attributes
    Given I run "GraphQlContentTypeScenario" configured as "http://{MAZE_ADDRESS}/<url_path>|||<content_type>|||<body>"
    And I wait to receive at least 1 span
    * a span field "kind" equals 3
    * a span field "name" matches the regex "GraphQL .* - <expected_name>$"
    * a span string attribute "bugsnag.span.category" equals "graphql"
    * a span string attribute "http.url" matches the regex "^http://.*/<url_path>$"
    * a span string attribute "http.method" equals "POST"

    Examples:
      | detection_method                    | url_path         | content_type          | body                                                                                                                                       | expected_name             |
      | Content-Type application/graphql    | traces           | application/graphql   | query GetCountries { countries { code name } }                                                                                             | query:GetCountries        |
      | URL /graphql + JSON body            | graphql          | application/json      | {\"query\": \"query FetchItems { items { id } }\", \"operationName\": \"FetchItems\"}                                                      | query:FetchItems          |
      | URL /api/graphql                    | api/graphql      | application/json      | {\"query\": \"mutation CreatePost($input: CreatePostInput!) { createPost(input: $input) { id } }\", \"operationName\": \"CreatePost\"}     | mutation:CreatePost       |
      | URL /api/v1/graphql                 | api/v1/graphql   | application/json      | {\"query\": \"subscription OnMessage { message { id text } }\", \"operationName\": \"OnMessage\"}                                          | subscription:OnMessage    |
      | URL /graphql/ trailing slash        | graphql          | application/json      | {\"query\": \"query GetProfile { profile { name } }\", \"operationName\": \"GetProfile\"}                                                  | query:GetProfile          |
      | Body inspection (non-graphql URL)   | custom-endpoint  | application/json      | {\"query\": \"mutation UpdateUser($id: ID!) { updateUser(id: $id) { id } }\", \"operationName\": \"UpdateUser\"}                           | mutation:UpdateUser       |
      | HTTP 400 error response             | graphql          | application/json      | {\"query\": \"query BadQuery { invalid }\", \"operationName\": \"BadQuery\"}                                                               | query:BadQuery            |
      | HTTP 401 unauthorized               | graphql          | application/json      | {\"query\": \"query GetSecret { secret { value } }\", \"operationName\": \"GetSecret\"}                                                    | query:GetSecret           |
      | HTTP 500 server error               | graphql          | application/json      | {\"query\": \"mutation FailOp { fail { msg } }\", \"operationName\": \"FailOp\"}                                                           | mutation:FailOp           |

  # Scenario 2
  Scenario Outline: Operation type "<op_type>" correctly extracted with name priority "<priority>"
    Given I run "GraphQlContentTypeScenario" configured as "http://{MAZE_ADDRESS}/graphql|||application/json|||<body>"
    And I wait to receive at least 1 span
    * a span field "name" matches the regex "^GraphQL .*/graphql - <op_type>:<expected_name>$"

    Examples:
      | op_type     | priority                 | body                                                                                           | expected_name         |
      | query       | operationName field (P1) | {\"query\": \"query GetUser { user { id } }\", \"operationName\": \"GetUser\"}                 | GetUser               |
      | mutation    | operationName field (P1) | {\"query\": \"mutation CreatePost { createPost { id } }\", \"operationName\": \"CreatePost\"}  | CreatePost            |

  # Scenario 3
  Scenario Outline: Display name follows format "GraphQL <url_path> - <op_type>:<op_name>" for <case>
    Given I run "GraphQlContentTypeScenario" configured as "http://{MAZE_ADDRESS}<url_path>|||application/json|||<body>"
    And I wait to receive at least 1 span
    Then a span field "name" matches the regex "^GraphQL .*<url_path> - (<op_type>)?(:)?(<op_name>)?$"

    Examples:
      | case                        | url_path      | body                                                                                      | op_type       | op_name    |
      | Normal query with name      | /graphql      | {\"query\": \"query GetUser { user { id } }\", \"operationName\": \"GetUser\"}            | query         | GetUser    |
      | Mutation with name          | /graphql      | {\"query\": \"mutation UpdateCart { cart { id } }\", \"operationName\": \"UpdateCart\"}   | mutation      | UpdateCart |
      | Subscription with name      | /graphql      | {\"query\": \"subscription OnNotify { notify { id } }\", \"operationName\": \"OnNotify\"} | subscription  | OnNotify   |
      | Anonymous (no name)         | /graphql      | {\"query\": \"query { user { id } }\"}                                                    | query         |            |
      | Unknown type + known name   | /graphql      | {\"query\": \"{ user { id } }\", \"operationName\": \"GetUser\"}                          | query         | GetUser    |
      | Custom endpoint path        | /api/graphql  | {\"query\": \"query GetUser { user { id } }\", \"operationName\": \"GetUser\"}            | query         | GetUser    |

  # Scenario 4
  Scenario Outline: Non-GraphQL request "<case>" retains network category
    Given I run "GraphQlContentTypeScenario" configured as "http://{MAZE_ADDRESS}/rest/users|||<content_type>|||<body>"
    And I wait to receive at least 1 span
    * a span string attribute "bugsnag.span.category" equals "network"
    * a span field "name" matches the regex "^\[HTTP/POST\]$"

    Examples:
      | case               | content_type     | body                                   |
      | Standard REST POST | application/json | {\"userId\": \"123\", \"action\": \"get\"} |

  # Scenario 5
  Scenario Outline: POST with <body_type> body does not crash and falls back to network
    Given I run "GraphQlContentTypeScenario" configured as "http://{MAZE_ADDRESS}/api/data|||application/json|||<body>"
    And I wait to receive at least 1 span
    * a span string attribute "bugsnag.span.category" equals "network"
    * a span field "name" matches the regex "^\[HTTP/POST\]$"

    Examples:
      | body_type         | body                    |
      | empty string      |                         |
      | malformed JSON    | {invalid json content   |
      | empty JSON object | {}                      |

  # Scenario 6
  Scenario Outline: Operation name "<name_type>" is handled without crash or data loss
    Given I run "GraphQlContentTypeScenario" configured as "http://{MAZE_ADDRESS}/graphql|||application/json|||<body>"
    And I wait to receive at least 1 span
    * a span field "name" matches the regex "^GraphQL .*/graphql - query:<expected_name>$"

    Examples:
      | name_type                     | body                                                                                                                                                                                                          | expected_name                                                                                 |
      | Very long name (128+ chars)   | {\"query\": \"query AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA { user { id } }\", \"operationName\": \"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\"} | AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA |
      | Underscore and version suffix | {\"query\": \"query Get_User_Profile_V2 { user { id } }\", \"operationName\": \"Get_User_Profile_V2\"}                                                                                                                  | Get_User_Profile_V2                                                                           |
      | Numeric suffix                | {\"query\": \"query GetUser123 { user { id } }\", \"operationName\": \"GetUser123\"}                                                                                                                                    | GetUser123                                                                                    |

  # Scenario 7
  Scenario: Batched GraphQL request with multiple operations does not crash the SDK
    Given I run "GraphQlContentTypeScenario" configured as "http://{MAZE_ADDRESS}/graphql|||application/json|||[{\"query\": \"query GetUser { user { id } }\", \"operationName\": \"GetUser\"}, {\"query\": \"query GetPosts { posts { id } }\", \"operationName\": \"GetPosts\"}]"
    And I wait to receive at least 1 span
    # The SDK successfully identifies this as GraphQL even in a batched array format
    Then a span string attribute "bugsnag.span.category" equals "graphql"
    # When multiple operations are present, the SDK typically picks the first one for the span name
    And a span field "name" matches the regex "^GraphQL .*/graphql - query:GetUser$"

  # Scenario 8
  Scenario: GET request to /graphql with query params does not crash
    Given I run "OkhttpSpanScenario" configured as "http://{MAZE_ADDRESS}/graphql?query={user{id}}&operationName=GetUser"
    And I wait to receive at least 1 span
    * a span field "kind" equals 3
    * a span string attribute "bugsnag.span.category" equals "graphql"
    * a span string attribute "http.method" equals "GET"
    * a span field "name" matches the regex "^GraphQL .*/graphql - query$"

  # Scenario9
  Scenario: Multiple GraphQL operations create distinct span names and coexist with network spans
      Given I run "MultipleGraphQlScenario" configured as "http://{MAZE_ADDRESS}"
      And I wait to receive 4 spans

      # Use "each" or specific existence checks to reset the search context
      Then 1 span field "name" matches the regex "GraphQL.*query:GetUser"
      And 1 span field "name" matches the regex "GraphQL.*mutation:CreatePost"
      And 1 span field "name" matches the regex "\[HTTP/GET\]"

      # For attributes, verify the counts in the total collection
      And 2 spans have a span string attribute "bugsnag.span.category" equal to "graphql"
      And 2 spans have a span string attribute "bugsnag.span.category" equal to "network"

  # Scenario 10
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

  # Scenario 11
  Scenario: GraphQL span with explicit first_class=false is not aggregated into span groups
    Given I run "GraphQlContentTypeScenario" configured as "http://{MAZE_ADDRESS}/traces|||application/graphql|||query GetCountries { countries { code name } }|||false"
    And I wait to receive at least 1 span
    * a span field "kind" equals 3
    * a span string attribute "bugsnag.span.category" equals "graphql"
    * a span bool attribute "bugsnag.span.first_class" is false

  # Scenario 12
  Scenario Outline: GraphQL span is created even when request <failure_type>
      Given I run "GraphQlContentTypeScenario" configured as "http://{MAZE_ADDRESS}/graphql|||application/graphql|||query GetUser { user { id } }|||true|||<failure_condition>"
      And I wait to receive at least 1 span
      Then the span field "name" matches the regex "^GraphQL .*/graphql - query(:GetUser)?$"
      And a span string attribute "bugsnag.span.category" equals "graphql"
      And a span field "status.code" equals <expected_status>

      Examples:
        | failure_type       | failure_condition             | expected_status |
        | times out          | times out after 30 seconds    | 2               |
        | connection refused | fails with connection refused | 2               |
        | returns empty body | completes with status 204     | 1               |

  # Scenario 13
  Scenario: Android SDK produces GraphQL span via supported GraphQL client library
    Given I run "ApolloScenario" configured as "http://{MAZE_ADDRESS}/graphql"
    And I wait to receive at least 1 span
    * a span field "kind" equals 3
    * a span string attribute "bugsnag.span.category" equals "graphql"
    * a span field "name" matches the regex "^GraphQL .*/graphql - query:TestQuery$"
    * every span attribute "graphql.document" does not exist

  # Scenario 15
  Scenario: Multiple identical GraphQL operations produce spans with consistent name for pipeline grouping
    Given I run "IdenticalGraphQlScenario" configured as "http://{MAZE_ADDRESS}"
    And I wait to receive 3 spans
    * every span field "name" matches the regex "^GraphQL .*/graphql - query:GetUser$"
    * every span string attribute "bugsnag.span.category" equals "graphql"
    * every span field "spanId" matches the regex "^[0-9a-f]{16}$"
    * every span field "traceId" matches the regex "^[0-9a-f]{32}$"

  # Scenario 16
  Scenario Outline: GraphQL response with <error_type> sets span status to <expected_status>
    Given I run "GraphQlContentTypeScenario" configured as "http://{MAZE_ADDRESS}/graphql|||application/json|||{\"query\": \"query GetUser { user { id } }\", \"operationName\": \"GetUser\"}|||<http_status>|||<body_content>"
    And I wait to receive at least 1 span
    * a span field "name" matches the regex "^GraphQL .*/graphql - query:GetUser$"
    * a span string attribute "bugsnag.span.category" equals "graphql"
    * a span integer attribute "http.status_code" equals <http_status>
    * a span field "status.code" equals <expected_status_code>

    Examples:
      | error_type                          | http_status | body_content                                                                              | expected_status   | expected_status_code |
      | HTTP 200 with errors array          | 200         | {\"data\": null, \"errors\": [{\"message\": \"User not found\"}]}                         | STATUS_CODE_ERROR | 2                    |
      | HTTP 200 with partial data + errors | 200         | {\"data\": {\"user\": {\"id\": \"1\"}}, \"errors\": [{\"message\": \"Field deprecated\"}]} | STATUS_CODE_ERROR | 2                    |
      | HTTP 200 success (no errors)        | 200         | {\"data\": {\"user\": {\"id\": \"1\", \"name\": \"John\"}}}                               | STATUS_CODE_OK    | 1                    |
      | HTTP 200 with empty errors array    | 200         | {\"data\": {\"user\": {\"id\": \"1\"}}, \"errors\": []}                                   | STATUS_CODE_OK    | 1                    |
      | HTTP 500 transport error            | 500         | {}                                                                                        | STATUS_CODE_ERROR | 2                    |
      | HTTP 401 unauthorized               | 401         | {}                                                                                        | STATUS_CODE_ERROR | 2                    |
