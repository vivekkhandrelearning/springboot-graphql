package com.example.dynamicgraphreportui;

import java.util.List;
import java.util.Map;

import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.TransactionContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

import com.abcde.tni.commonutils.neo4j.DatabaseDriver;

import graphql.scalars.ExtendedScalars;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLList;

@Configuration
public class GraphQLConfig {

    private final SchemaBasedQueryGenerator schemaBasedQueryGenerator;
    private final DatabaseDriver databaseDriver;
    private final QueryService queryService;

    public GraphQLConfig(SchemaBasedQueryGenerator schemaBasedQueryGenerator, DatabaseDriver databaseDriver, QueryService queryService) {
        this.schemaBasedQueryGenerator = schemaBasedQueryGenerator;
        this.databaseDriver = databaseDriver;
        this.queryService = queryService;
    }

    @Bean
    public RuntimeWiringConfigurer runtimeWiringConfigurer() {
        return wiringBuilder -> wiringBuilder
                .scalar(ExtendedScalars.Json)
                .type("Query", builder -> builder
                        .dataFetcher("runQuery", runQueryDataFetcher())
                        .defaultDataFetcher(genericDataFetcher())
                );
    }

    private DataFetcher<Object> genericDataFetcher() {
        return environment -> {
            String cypher = schemaBasedQueryGenerator.generateQuery(environment);
            System.out.println("Generic DataFetcher Cypher: " + cypher);
            
            Map<String, Object> args = environment.getArguments();
            List<Map<String, Object>> results = executeQuery(cypher, args);
            
            boolean isList = environment.getFieldDefinition().getType() instanceof GraphQLList;
            
            if (isList) {
                return results.stream()
                        .map(r -> r.values().isEmpty() ? null : r.values().iterator().next())
                        .collect(java.util.stream.Collectors.toList());
            } else {
                if (results.isEmpty()) return null;
                Map<String, Object> firstRow = results.get(0);
                return firstRow.values().isEmpty() ? null : firstRow.values().iterator().next();
            }
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
        try (Session session = databaseDriver.sessionFor()) {
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
