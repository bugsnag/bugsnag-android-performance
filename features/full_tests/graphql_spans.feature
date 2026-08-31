Feature: GraphQL Spans

  # Scenario 1
  Scenario Outline: GraphQL detected via <detection_method> produces correct span with full attributes
    Given I run "GraphQlContentTypeScenario" configured as "<url>|||<content_type>|||<body>|||<status>|||{}"
    And I wait to receive at least 1 span
    * a span field "name" matches the regex "<name_regex>"
    * a span string attribute "bugsnag.span.category" equals "graphql"
    * a span bool attribute "bugsnag.span.first_class" is true
    * a span string attribute "http.method" equals "POST"
    * a span integer attribute "http.status_code" equals "<status>"
    * every span attribute "graphql.document" does not exist
    * every span attribute "graphql.variables" does not exist
    * every span attribute "graphql.operation.type" does not exist
    * every span attribute "graphql.operation.name" does not exist

    Examples:
      | status | detection_method                  | name_regex                                                      | url                                            | content_type        | body                                                                                                                                   |
      | 200    | Content-Type application/graphql  | ^GraphQL .+/data - query:GetUserProfile$                        | https://api.example.com/data                   | application/graphql | query GetUserProfile { user { id name } }                                                                                              |
      | 200    | URL /graphql + JSON body          | ^GraphQL .+/graphql - query:FetchItems$                         | https://api.example.com/graphql                | application/json    | {\"query\": \"query FetchItems { items { id } }\", \"operationName\": \"FetchItems\"}                                                  |
      | 200    | URL /api/graphql                  | ^GraphQL .+/api/graphql - mutation:CreatePost$                  | https://api.example.com/api/graphql            | application/json    | {\"query\": \"mutation CreatePost($input: CreatePostInput!) { createPost(input: $input) { id } }\", \"operationName\": \"CreatePost\"} |
      | 200    | URL /api/v1/graphql               | ^GraphQL .+/api/v1/graphql - subscription:OnMessage$            | https://api.example.com/api/v1/graphql         | application/json    | {\"query\": \"subscription OnMessage { message { id text } }\", \"operationName\": \"OnMessage\"}                                      |
      | 200    | URL /graphql/ trailing slash      | ^GraphQL .+/graphql/ - query:GetProfile$                        | https://api.example.com/graphql/               | application/json    | {\"query\": \"query GetProfile { profile { name } }\", \"operationName\": \"GetProfile\"}                                              |
      | 200    | Body inspection (non-graphql URL) | ^GraphQL .+/custom-endpoint - mutation:UpdateUser$              | https://api.example.com/custom-endpoint        | application/json    | {\"query\": \"mutation UpdateUser($id: ID!) { updateUser(id: $id) { id } }\", \"operationName\": \"UpdateUser\"}                       |
      | 400    | HTTP 400 error response           | ^GraphQL .+/graphql - query:BadQuery$                           | https://api.example.com/graphql                | application/json    | {\"query\": \"query BadQuery { invalid }\", \"operationName\": \"BadQuery\"}                                                           |
      | 401    | HTTP 401 unauthorized             | ^GraphQL .+/graphql - query:GetSecret$                          | https://api.example.com/graphql                | application/json    | {\"query\": \"query GetSecret { secret { value } }\", \"operationName\": \"GetSecret\"}                                                |
      | 500    | HTTP 500 server error             | ^GraphQL .+/graphql - mutation:FailOp$                          | https://api.example.com/graphql                | application/json    | {\"query\": \"mutation FailOp { fail { msg } }\", \"operationName\": \"FailOp\"}                                                       |


  # Scenario 2
  Scenario Outline: Operation type "<op_type>" correctly extracted with name priority "<priority>"
    Given I run "GraphQlContentTypeScenario" configured as "https://api.example.com/graphql|||application/json|||<body>|||200|||{}"
    And I wait to receive at least 1 span
    * a span field "name" matches the regex "<name_regex>"
    * a span string attribute "bugsnag.span.category" equals "graphql"
    * a span bool attribute "bugsnag.span.first_class" is true
    * a span string attribute "http.method" equals "POST"
    * a span integer attribute "http.status_code" equals "200"
    * every span attribute "graphql.document" does not exist
    * every span attribute "graphql.variables" does not exist
    * every span attribute "graphql.operation.type" does not exist
    * every span attribute "graphql.operation.name" does not exist

    Examples:
      | priority                              | op_type     | name_regex                                              | body                                                                                                      |
      | operationName field (P1)              | query       | ^GraphQL .+/graphql - query:GetUser$                    | {\"query\": \"query GetUser { user { id } }\", \"operationName\": \"GetUser\"}                            |
      | operationName field (P1)              | mutation    | ^GraphQL .+/graphql - mutation:CreatePost$              | {\"query\": \"mutation CreatePost { createPost { id } }\", \"operationName\": \"CreatePost\"}             |
      | operationName field (P1)              | subscription| ^GraphQL .+/graphql - subscription:OnMsg$               | {\"query\": \"subscription OnMsg { message { id } }\", \"operationName\": \"OnMsg\"}                      |
      | document parsing (P2, no field)       | query       | ^GraphQL .+/graphql - query:FetchOrders$                | {\"query\": \"query FetchOrders { orders { id total } }\"}                                                |
      | document parsing (P2, no field)       | mutation    | ^GraphQL .+/graphql - mutation:DeleteItem$              | {\"query\": \"mutation DeleteItem { deleteItem { success } }\"}                                           |
      | anonymous (P3, both type & name null) | (anonymous) | ^GraphQL .+/graphql - query$                            | {\"query\": \"{ user { id name } }\"}                                                                     |
      | operationName overrides document name | query       | ^GraphQL .+/graphql - query:FieldName$                  | {\"query\": \"query DocumentName { user { id } }\", \"operationName\": \"FieldName\"}                     |
      | type present, name anonymous          | query       | ^GraphQL .+/graphql - query$                            | {\"query\": \"query { user { id } }\"}                                                                    |


# Scenario 3
  Scenario Outline: Span name follows correct format for <description>
    Given I run "GraphQlContentTypeScenario" configured as "<url>|||application/json|||<body>|||200|||{}"
    And I wait to receive at least 1 span
    * a span field "name" matches the regex "<name_regex>"
    * a span string attribute "bugsnag.span.category" equals "graphql"
    * a span bool attribute "bugsnag.span.first_class" is true
    * a span string attribute "http.method" equals "POST"
    * a span integer attribute "http.status_code" equals "200"
    * every span attribute "graphql.document" does not exist
    * every span attribute "graphql.variables" does not exist
    * every span attribute "graphql.operation.type" does not exist
    * every span attribute "graphql.operation.name" does not exist

    Examples:
      | description                       | url                                      | name_regex                                              | body                                                                                                      |
      | query with name                   | https://api.example.com/graphql          | ^GraphQL .+/graphql - query:GetUser$                    | {\"query\": \"query GetUser { user { id } }\", \"operationName\": \"GetUser\"}                            |
      | mutation with name                | https://api.example.com/graphql          | ^GraphQL .+/graphql - mutation:UpdateCart$              | {\"query\": \"mutation UpdateCart { cart { id } }\", \"operationName\": \"UpdateCart\"}                   |
      | subscription with name            | https://api.example.com/graphql          | ^GraphQL .+/graphql - subscription:OnNotify$            | {\"query\": \"subscription OnNotify { notify { id } }\", \"operationName\": \"OnNotify\"}               |
      | anonymous query (no name)         | https://api.example.com/graphql          | ^GraphQL .+/graphql - query$                            | {\"query\": \"query { user { id } }\"}                                                                    |
      | unknown type with operationName   | https://api.example.com/graphql          | ^GraphQL .+/graphql - query:GetUser$                    | {\"query\": \"{ user { id } }\", \"operationName\": \"GetUser\"}                                          |
      | custom endpoint path              | https://api.example.com/api/graphql      | ^GraphQL .+/api/graphql - query:GetUser$                | {\"query\": \"query GetUser { user { id } }\", \"operationName\": \"GetUser\"}                            |


  # Scenario 4
  Scenario Outline: Non-GraphQL request "<case>" retains network category
    Given I run "GraphQlContentTypeScenario" configured as "<url>|||<content_type>|||<body>|||200|||{}|||<method>"
    And I wait to receive at least 1 span
    * a span string attribute "bugsnag.span.category" equals "network"
    * a span field "name" matches the regex "^\[HTTP/<method>\]$"
    * a span string attribute "http.method" equals "<method>"
    * every span field "name" does not match the regex "GraphQL"

    Examples:
      | case                                  | method | url                                      | content_type     | body                                                      |
      | Standard REST POST                    | POST   | https://api.example.com/rest/users       | application/json | {\"userId\": \"123\", \"action\": \"get\"}                |
      | JSON with query key (not GraphQL)     | POST   | https://api.example.com/api/search       | application/json | {\"query\": \"shoes\", \"page\": 1}                       |
      | Natural language query (ambiguous)    | POST   | https://api.example.com/api/search       | application/json | {\"query\": \"find all users named John\", \"limit\": 10} |
      | GET to REST endpoint                  | GET    | https://api.example.com/api/users/123    | application/json |                                                           |
      | XML content type                      | POST   | https://api.example.com/api/data         | application/xml  | <request><query>GetUser</query></request>                 |
      | text/html content type                | POST   | https://api.example.com/submit           | text/html        | <html><body>test</body></html>                            |

# Scenario 5
  Scenario Outline: POST with <body_type> body does not crash and falls back to network
    Given I run "GraphQlContentTypeScenario" configured as "https://api.example.com/api/data|||application/json|||<body>|||200|||{}|||POST"
    And I wait to receive at least 1 span
    * a span string attribute "bugsnag.span.category" equals "network"
    * a span field "name" matches the regex "^\[HTTP/POST\]$"
    * every span field "name" does not match the regex "GraphQL"
    * a span string attribute "http.method" equals "POST"
    * every span attribute "graphql.document" does not exist
    * every span attribute "graphql.variables" does not exist
    * every span attribute "graphql.operation.type" does not exist
    * every span attribute "graphql.operation.name" does not exist

    Examples:
      | body_type         | body                    |
      | empty string      |                         |
      | malformed JSON    | {invalid json content   |
      | empty JSON object | {}                      |

  # Scenario 6
  Scenario Outline: Operation name "<name_type>" is handled without crash or data loss
    Given I run "GraphQlContentTypeScenario" configured as "https://api.example.com/graphql|||application/json|||<body>|||200|||{}|||POST"
    And I wait to receive at least 1 span
    * a span field "name" matches the regex "^GraphQL .+/graphql - query:<expected_name>$"
    * a span string attribute "bugsnag.span.category" equals "graphql"
    * a span bool attribute "bugsnag.span.first_class" is true
    * a span string attribute "http.method" equals "POST"
    * a span integer attribute "http.status_code" equals "200"
    * every span field "name" matches the regex "GraphQL"
    * every span attribute "graphql.document" does not exist
    * every span attribute "graphql.variables" does not exist
    * every span attribute "graphql.operation.type" does not exist
    * every span attribute "graphql.operation.name" does not exist

    Examples:
      | name_type                     | body                                                                                                                                                                                                          | expected_name                                                                                 |
      | Very long name (128+ chars)   | {\"query\": \"query AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA { user { id } }\", \"operationName\": \"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\"} | AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA |
      | Underscore and version suffix | {\"query\": \"query Get_User_Profile_V2 { user { id } }\", \"operationName\": \"Get_User_Profile_V2\"}                                                                                                                  | Get_User_Profile_V2                                                                           |
      | Numeric suffix                | {\"query\": \"query GetUser123 { user { id } }\", \"operationName\": \"GetUser123\"}                                                                                                                                    | GetUser123                                                                                    |

  # Scenario 7
  Scenario: Batched GraphQL request with multiple operations does not crash the SDK
    Given I run "GraphQlContentTypeScenario" configured as "https://api.example.com/graphql|||application/json|||[{\"query\": \"query GetUser { user { id } }\", \"operationName\": \"GetUser\"}, {\"query\": \"query GetPosts { posts { id } }\", \"operationName\": \"GetPosts\"}]|||200|||{}|||POST"
    And I wait to receive at least 1 span
    # The SDK successfully identifies this as GraphQL even in a batched array format
    Then a span string attribute "bugsnag.span.category" equals "graphql"
    * a span bool attribute "bugsnag.span.first_class" is true
    * a span string attribute "http.method" equals "POST"
    * a span integer attribute "http.status_code" equals "200"
    * every span attribute "graphql.document" does not exist
    * every span attribute "graphql.variables" does not exist
    * every span attribute "graphql.operation.type" does not exist
    * every span attribute "graphql.operation.name" does not exist
    # When multiple operations are present, the SDK typically picks the first one for the span name
    And a span field "name" matches the regex "^GraphQL .+/graphql - query:GetUser$"

  # Scenario 8
  Scenario: GET request to /graphql with query params does not crash
    Given I run "GraphQlContentTypeScenario" configured as "https://api.example.com/graphql?query=%7Buser%7Bid%7D%7D&operationName=GetUser|||application/json||||||200|||{}|||GET"
    And I wait to receive at least 1 span
    * a span field "kind" equals 3
    * a span string attribute "bugsnag.span.category" equals "graphql"
    * a span bool attribute "bugsnag.span.first_class" is true
    * a span string attribute "http.method" equals "GET"
    * a span integer attribute "http.status_code" equals "200"
    * a span field "name" matches the regex "^GraphQL .+/graphql - query:GetUser$"

  # Scenario 9
  Scenario: Multiple GraphQL operations create distinct span names and coexist with network spans
    Given I run "MultipleGraphQlScenario"
    And I wait to receive 4 spans
    * 2 spans have field "name" matching the regex "^GraphQL .+/graphql - query:GetUser$"
    * 1 spans have field "name" matching the regex "^GraphQL .+/graphql - mutation:CreatePost$"
    * 3 spans have string attribute "bugsnag.span.category" equals "graphql"
    * 1 spans have string attribute "bugsnag.span.category" equals "network"
    * a span field "name" matches the regex "^\[HTTP/GET\]$"
    * every span attribute "graphql.document" does not exist
    * every span attribute "graphql.variables" does not exist
    * every span attribute "graphql.operation.type" does not exist
    * every span attribute "graphql.operation.name" does not exist

  # Scenario 10
  Scenario: GraphQL span payload contains only HTTP attributes and category - no GraphQL-specific metadata
    Given I run "GraphQlContentTypeScenario" configured as "https://api.example.com/graphql|||application/json|||{\"query\": \"query GetUser { user { id secret } }\", \"operationName\": \"GetUser\", \"variables\": {\"sensitiveData\": \"should-not-leak\"}}|||200|||{\"data\": {\"user\": {\"id\": \"1\", \"secret\": \"user_secret_123\"}}}|||POST"
    And I wait to receive at least 1 span
    * a span string attribute "bugsnag.span.category" equals "graphql"
    * a span bool attribute "bugsnag.span.first_class" is true
    * a span string attribute "http.method" equals "POST"
    * a span string attribute "http.url" equals "https://api.example.com/graphql"
    * a span integer attribute "http.status_code" equals "200"
    * every span attribute "graphql.operation.name" does not exist
    * every span attribute "graphql.operation.type" does not exist
    * every span attribute "graphql.document" does not exist
    * every span attribute "graphql.variables" does not exist
    * the span payload does not contain "user_secret_123"
    * the span payload does not contain "sensitiveData"

  # Scenario 11
  Scenario: GraphQL span with explicit first_class=false is not aggregated into span groups
    Given I run "GraphQlContentTypeScenario" configured as "http://{MAZE_ADDRESS}/traces|||application/graphql|||query GetCountries { countries { code name } }|||false"
    And I wait to receive at least 1 span
    * a span field "kind" equals 3
    * a span string attribute "bugsnag.span.category" equals "graphql"
    * a span bool attribute "bugsnag.span.first_class" is false

  # Scenario 12

  # Scenario 13
  Scenario: Android SDK produces GraphQL span via supported GraphQL client library
    Given I run "ApolloScenario" configured as "https://api.example.com/graphql"
    And I wait to receive at least 1 span
    * a span field "kind" equals 3
    * a span string attribute "bugsnag.span.category" equals "graphql"
    * a span bool attribute "bugsnag.span.first_class" is true
    * a span string attribute "http.method" equals "POST"
    * a span string attribute "http.url" equals "https://api.example.com/graphql"
    * a span integer attribute "http.status_code" equals "200"
    * a span field "name" matches the regex "^GraphQL .+/graphql - query:TestQuery$"
    * every span field "name" matches the regex "GraphQL"
    * every span attribute "graphql.document" does not exist
    * every span attribute "graphql.variables" does not exist
    * every span attribute "graphql.operation.name" does not exist
    * every span attribute "graphql.operation.type" does not exist

  # Scenario 15
  Scenario: Multiple identical GraphQL operations produce spans with consistent name for pipeline grouping
    Given I run "IdenticalGraphQlScenario" configured as "https://api.example.com/graphql"
    And I wait to receive 3 spans
    * every span field "name" matches the regex "^GraphQL .+/graphql - query:GetUser$"
    * every span string attribute "bugsnag.span.category" equals "graphql"
    * every span bool attribute "bugsnag.span.first_class" is true
    * every span field "spanId" matches the regex "^[0-9a-f]{16}$"
    * every span field "spanId" value is distinct
    * every span field "traceId" matches the regex "^[0-9a-f]{32}$"

  # Scenario 16
