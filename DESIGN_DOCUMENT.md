# GraphQL Service Enhancements Design Document

## 1. Overview
This document details the architectural changes and enhancements implemented in the **Inventory GraphQL Service**. The primary goals were to introduce dynamic schema-based query generation, robust rate limiting, secure logging, standardized error handling, and flexible reporting capabilities with advanced filtering and pagination.

## 2. Architecture

The service follows a layered architecture leveraging Spring Boot and GraphQL Java.

*   **Controller Layer**: Standard Spring GraphQL controllers handle incoming requests.
*   **Data Fetcher Layer**: Custom `DataFetcher` implementations (`GraphQLConfig`) bridge GraphQL fields to backend logic.
*   **Service Layer**:
    *   `QueryService`: Orchestrates Cypher query execution against Neo4j.
    *   `SchemaBasedQueryGenerator`: dynamically builds Cypher queries based on the GraphQL schema and selection set.
    *   `RateLimitingService`: Manages token buckets for API rate limiting.
*   **Data Access Layer**: `DatabaseDriver` (Neo4j Driver) for direct database interaction.

## 3. Key Features

### 3.1 Dynamic Schema-Based Query Generation
The `SchemaBasedQueryGenerator` component allows the service to automatically generate optimized Cypher queries based on the fields requested in the GraphQL query.

*   **Mechanism**: It inspects the `DataFetchingEnvironment` to identify selected fields and relationships.
*   **Benefits**: Reduces the need for manually writing Cypher for every new field, ensuring the backend evolves automatically with Schema changes.
*   **Relationship Handling**: Automatically generates `OPTIONAL MATCH` clauses for requested related nodes.

### 3.2 Configurable Cypher Queries (`queries.yml`)
To support complex reports like `customFullReport`, the service uses externalized configuration.

*   **Structure**:
    ```yaml
    queryName:
      cypher: "MATCH (n:Label) WHERE {{WHERE_CLAUSE_1}} ... RETURN n"
      fieldMapping1:
        graphql_field: "db.property"
      fieldMapping2: ...
    ```
*   **Multiple WHERE Clauses**: Supports `{{WHERE_CLAUSE_1}}`, `{{WHERE_CLAUSE_2}}`, etc., allowing filtering at different stages of the query (e.g., initial match vs. post-aggregation).
*   **Type-Safe Filtering**: Mappings can specify data types (`NUMBER`, `DATETIME`) to ensure correct parameter conversion and Cypher syntax generation (e.g., `dbField > datetime($param)`).

### 3.3 Rate Limiting
Implemented using **Bucket4j** to protect the API from abuse.

*   **Granularity**: IP-based rate limiting.
*   **Configuration**:
    *   Capacity and refill rate configurable via `application.yaml`.
*   **Implementation**: `RateLimitFilter` intercepts requests to `/api/v1/graphql` and consumes tokens. Returns `429 Too Many Requests` when limits are exceeded, with standard headers (`X-Rate-Limit-Remaining`, `X-Rate-Limit-Retry-After-Seconds`).

### 3.4 Secure Logging
Integrated **OWASP ESAPI** for secure logging to prevent log injection attacks and ensure compliance.

*   **Configuration**: Controlled via `esapi.properties` and `esapi-java-logging.properties`.
*   **Usage**: All sensitive query executions and service interactions are logged using the ESAPI logger.

### 3.5 Standardized Error Handling
Introduced `GraphQlApplicationException` to provide consistent error responses.

*   **Structure**: Includes distinct `errorCode` and `message`.
*   **Behavior**: Exceptions during schema generation or database execution are caught and re-thrown as this custom exception, ensuring GraphQL clients receive structured error data.

### 3.6 Advanced Pagination and Filtering
The `customFullReport` query now supports:

*   **Direct Pagination**: Uses `limit` (Int) and `offset` (Int) arguments directly, simplifying the API surface compared to nested pagination objects.
*   **Complex Filtering**:
    *   **Operators**: `EQ` (Equal), `IN` (In List), `CONTAINS` (String contains), `GT` (Greater Than), `LT` (Less Than), `NEQ` (Not Equal).
    *   **Logic**: `QueryService` dynamically builds the `WHERE` clause based on provided filters and the configured field mappings.

## 4. API Design

### 4.1 `customFullReport`
Retrieves a detailed report for a specific entity type.

**Signature:**
```graphql
customFullReport(
  type: String!,
  filters: [FilterInput],
  sort: [SortInput],
  limit: Int,
  offset: Int
): ReportResult
```

**Types:**
```graphql
type ReportResult {
  rows: [JSON]
  pageInfo: PageInfo
}
```

**Inputs:**
*   `FilterInput`: `{ field: String!, op: Op!, values: [String] }`
*   `SortInput`: `{ field: String!, direction: SortDirection }`

**Output:**
*   Returns a `ReportResult` object containing `rows` (list of JSON objects).

**Request Examples:**

*Basic Query:*
```graphql
query {
  customFullReport(
    type: "Antenna",
    limit: 10,
    offset: 0
  ) {
    rows
  }
}
```

*Query with Filters:*
```graphql
query {
  customFullReport(
    type: "Antenna",
    filters: [
      { field: "manufacturer_type", op: EQ, values: ["SomeType"] },
      { field: "resource_status", op: IN, values: ["INSTALLED", "PLANNED"] }
    ],
    limit: 20,
    offset: 0
  ) {
    rows
  }
}
```

*Query with Sorting:*
```graphql
query {
  customFullReport(
    type: "Antenna",
    sort: [
      { field: "manufacturer_type", direction: DESC }
    ],
    limit: 10,
    offset: 0
  ) {
    rows
  }
}
```

## 5. Configuration

### 5.1 `application.yaml`
Standard Spring Boot configuration plus application-specific settings (e.g., rate limit capacity).

### 5.2 `queries.yml`
Central repository for named Cypher queries and their field mappings. This allows modifying database interaction logic without recompiling the code.

## 6. Testing Strategy

*   **Unit Tests**: Comprehensive JUnit 5 tests using Mockito for individual services (`QueryServiceTest`, `SchemaBasedQueryGeneratorTest`, `RateLimitingServiceTest`).
*   **Integration Tests**: `@SpringBootTest` based tests (`CustomFullReportTest`) verifying the end-to-end flow from GraphQL controller to mocked database responses, ensuring schema validity and correct wiring.
*   **Coverage**: Code coverage reporting enabled via JaCoCo.
