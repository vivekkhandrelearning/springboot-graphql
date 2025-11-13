package com.example.dynamicgraphreportui;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.TransactionContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

import graphql.scalars.ExtendedScalars;
import graphql.schema.DataFetcher;

@Configuration
public class GraphQLConfig {

    private final SchemaBasedQueryGenerator schemaBasedQueryGenerator;
    private final Driver driver;
    private final QueryService queryService;

    public GraphQLConfig(SchemaBasedQueryGenerator schemaBasedQueryGenerator, Driver driver, QueryService queryService) {
        this.schemaBasedQueryGenerator = schemaBasedQueryGenerator;
        this.driver = driver;
        this.queryService = queryService;
    }

    @Bean
    public RuntimeWiringConfigurer runtimeWiringConfigurer() {
        return wiringBuilder -> wiringBuilder
                .scalar(ExtendedScalars.Json)
                .type("Query", builder -> builder
                        .dataFetcher("devices", devicesDataFetcher())
                        .dataFetcher("device", deviceDataFetcher())
                        .dataFetcher("shelves", shelvesDataFetcher())
                        .dataFetcher("shelf", shelfDataFetcher())
                        .dataFetcher("runQuery", runQueryDataFetcher())
                );
    }

    private DataFetcher<Object> devicesDataFetcher() {
        return environment -> {
            String cypher = schemaBasedQueryGenerator.generateQuery(environment);
            System.out.println("Schema-based generated Cypher for devices: " + cypher);
            List<Map<String, Object>> results = executeQuery(cypher, Collections.emptyMap());
            return results.stream().map(r -> r.get("device")).collect(java.util.stream.Collectors.toList());
        };
    }

    private DataFetcher<Object> deviceDataFetcher() {
        return environment -> {
            String cypher = schemaBasedQueryGenerator.generateQuery(environment);
            System.out.println("Schema-based generated Cypher for device: " + cypher);
            String id = environment.getArgument("id");
            Map<String, Object> parameters = Map.of("id", id);
            List<Map<String, Object>> results = executeQuery(cypher, parameters);
            return results.isEmpty() ? null : results.get(0).get("device");
        };
    }

    private DataFetcher<Object> shelvesDataFetcher() {
        return environment -> {
            String cypher = schemaBasedQueryGenerator.generateQuery(environment);
            System.out.println("Schema-based generated Cypher for shelves: " + cypher);
            List<Map<String, Object>> results = executeQuery(cypher, Collections.emptyMap());
            return results.stream().map(r -> r.get("shelf")).collect(java.util.stream.Collectors.toList());
        };
    }

    private DataFetcher<Object> shelfDataFetcher() {
        return environment -> {
            String cypher = schemaBasedQueryGenerator.generateQuery(environment);
            System.out.println("Schema-based generated Cypher for shelf: " + cypher);
            String id = environment.getArgument("id");
            Map<String, Object> parameters = Map.of("id", id);
            List<Map<String, Object>> results = executeQuery(cypher, parameters);
            return results.isEmpty() ? null : results.get(0).get("shelf");
        };
    }

    private DataFetcher<Object> runQueryDataFetcher() {
        return environment -> {
            String queryName = environment.getArgument("queryName");
            Map<String, Object> parameters = environment.getArgument("parameters");
            System.out.println("GraphQLConfig.runQueryDataFetcher called with queryName: " + queryName + ", parameters: " + parameters);
            return queryService.getQueryResult(queryName, parameters);
        };
    }

    private List<Map<String, Object>> executeQuery(String cypher, Map<String, Object> parameters) {
        try (Session session = driver.session(SessionConfig.forDatabase("neo4jmitsonly1"))) {
            List<Record> recordList = executeRead(session, cypher, parameters);
            return recordList.stream()
                    .map(record -> record.asMap())
                    .toList();
        }
    }

    /**
     * Generic method to execute a read-only query and return the results.
     *
     * @param session     The database session within which the query is executed.
     * @param query       The Cypher query to execute.
     * @param propertyMap Parameters for the query, if any.
     * @return List of records resulting from the query.
     */
    public List<Record> executeRead(Session session, String query, Map<String, Object> propertyMap) {
        return session.executeRead(context -> executeTransaction(context, query, propertyMap).list());
    }

    /**
     * Generic method to execute a read-only query and return the results.
     *
     * @param tx          The database session within which the query is executed.
     * @param query       The Cypher query to execute.
     * @param propertyMap Parameters for the query, if any.
     * @return List of records resulting from the query.
     */
    private Result executeTransaction(TransactionContext tx, String query, Map<String, Object> propertyMap) {
        return tx.run(query, propertyMap);
    }

}
