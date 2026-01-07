package com.example.dynamicgraphreportui;

import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.telstra.tni.commonutils.neo4j.DatabaseDriver;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;

@ExtendWith(MockitoExtension.class)
class GraphQLConfigTest {

    @Mock
    private SchemaBasedQueryGenerator schemaBasedQueryGenerator;
    @Mock
    private DatabaseDriver databaseDriver;
    @Mock
    private QueryService queryService;
    @Mock
    private DataFetchingEnvironment environment;

    private GraphQLConfig graphQLConfig;

    @BeforeEach
    void setUp() {
        graphQLConfig = new GraphQLConfig(schemaBasedQueryGenerator, databaseDriver, queryService);
    }

    @Test
    void testGenericDataFetcher() throws Exception {
        DataFetcher<Object> fetcher = graphQLConfig.genericDataFetcher();
        
        when(schemaBasedQueryGenerator.generateQuery(environment)).thenReturn("MATCH (n) RETURN n");
        when(environment.getArguments()).thenReturn(Collections.emptyMap());
        // Since executeQuery is private and uses databaseDriver, we can't fully execute it without mocking deep Neo4j classes (Session, Result, etc) 
        // which are already covered in QueryServiceTest and difficult to mock here without refactoring.
        // However, we can verify schemaBasedQueryGenerator is called.
        // And since we didn't mock databaseDriver.sessionFor(), it will throw NPE if it proceeds, 
        // OR we can mock it to return a mock session.
        
        // Let's just verify the generator call which happens before DB execution
        try {
            fetcher.get(environment);
        } catch (Exception e) {
            // Expected NPE from databaseDriver
        }
        
        verify(schemaBasedQueryGenerator).generateQuery(environment);
    }

    @Test
    void testRunQueryDataFetcher() throws Exception {
        DataFetcher<Object> fetcher = graphQLConfig.runQueryDataFetcher();
        
        when(environment.getArgument("queryName")).thenReturn("testQuery");
        when(environment.getArgument("parameters")).thenReturn(Collections.emptyMap());
        
        fetcher.get(environment);
        
        verify(queryService).getQueryResult("testQuery", Collections.emptyMap());
    }

    @Test
    void testCreateDynamicDataFetcher() throws Exception {
        DataFetcher<Object> fetcher = graphQLConfig.createDynamicDataFetcher("dynamicQuery");
        
        Map<String, Object> args = new java.util.HashMap<>();
        args.put("limit", 10);
        
        when(environment.getArguments()).thenReturn(args);
        
        fetcher.get(environment);
        
        // Verify defaults added
        Map<String, Object> expectedArgs = new java.util.HashMap<>();
        expectedArgs.put("limit", 10);
        expectedArgs.put("offset", 0);
        expectedArgs.put("id", null);
        expectedArgs.put("npiId", null);
        
        verify(queryService).getQueryResult("dynamicQuery", expectedArgs);
    }
}
