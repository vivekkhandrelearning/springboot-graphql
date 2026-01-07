package com.example.dynamicgraphreportui;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;

import com.abcde.tni.commonutils.neo4j.DatabaseDriver;

@ExtendWith(MockitoExtension.class)
class QueryServiceTest {

    @Mock
    private DatabaseDriver databaseDriver;
    @Mock
    private Session session;
    @Mock
    private Result result;

    private QueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = new QueryService(databaseDriver);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getQueryResult_ExecutesCypherAndConvertsRecords() {
        when(databaseDriver.sessionFor()).thenReturn(session);
        when(session.run(anyString(), any(Map.class))).thenReturn(result);

        // Mock Record and Value
        Record record = mock(Record.class);
        Value value = mock(Value.class);
        when(record.keys()).thenReturn(Collections.singletonList("key"));
        when(record.get("key")).thenReturn(value);
        when(value.asObject()).thenReturn("value");
        // Ensure default type check returns false so it falls back to asObject
        when(value.hasType(any())).thenReturn(false);

        // Mock Result iterator
        when(result.list(any(Function.class))).thenAnswer(invocation -> {
            Function<Record, Map<String, Object>> mapper = invocation.getArgument(0);
            return Collections.singletonList(mapper.apply(record));
        });

        List<Object> actual = (List<Object>) queryService.getQueryResult("testQuery", new HashMap<>());

        assertNotNull(actual);
        assertEquals(1, actual.size());
        Map<String, Object> row = (Map<String, Object>) actual.get(0);
        assertEquals("value", row.get("key"));
        
        verify(session).run(anyString(), any(Map.class));
    }
    
    @Test
    @SuppressWarnings("unchecked")
    void getQueryResult_HandlesFilters_MissingFieldMapping() {
        when(databaseDriver.sessionFor()).thenReturn(session);
        when(session.run(anyString(), any(Map.class))).thenReturn(result);
        when(result.list(any(Function.class))).thenReturn(Collections.emptyList());

        Map<String, Object> params = new HashMap<>();
        params.put("filters", Collections.singletonList(
            Map.of("field", "non_existent_field", "op", "EQ", "values", Collections.singletonList("Value"))
        ));
        
        queryService.getQueryResult("getAntennaReport", params);
        
        // When field mapping is missing, buildWhereClause returns empty string
        // The query service replaces {{WHERE_CLAUSE_X}} with empty string in that case
        // So we expect the query to NOT contain "non_existent_field" logic, essentially it's just the base query without filters
        verify(session).run(anyString(), any(Map.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getQueryResult_HandlesFilters_NullFilters() {
        when(databaseDriver.sessionFor()).thenReturn(session);
        when(session.run(anyString(), any(Map.class))).thenReturn(result);
        when(result.list(any(Function.class))).thenReturn(Collections.emptyList());

        Map<String, Object> params = new HashMap<>();
        params.put("filters", null);
        
        queryService.getQueryResult("getAntennaReport", params);
        
        verify(session).run(anyString(), any(Map.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getQueryResult_HandlesFilters_EmptyFilters() {
        when(databaseDriver.sessionFor()).thenReturn(session);
        when(session.run(anyString(), any(Map.class))).thenReturn(result);
        when(result.list(any(Function.class))).thenReturn(Collections.emptyList());

        Map<String, Object> params = new HashMap<>();
        params.put("filters", Collections.emptyList());
        
        queryService.getQueryResult("getAntennaReport", params);
        
        verify(session).run(anyString(), any(Map.class));
    }
    
    @Test
    @SuppressWarnings("unchecked")
    void getQueryResult_HandlesFilters_UnknownOp() {
        when(databaseDriver.sessionFor()).thenReturn(session);
        when(session.run(anyString(), any(Map.class))).thenReturn(result);
        when(result.list(any(Function.class))).thenReturn(Collections.emptyList());

        Map<String, Object> params = new HashMap<>();
        params.put("filters", Collections.singletonList(
            Map.of("field", "manufacturer_type", "op", "UNKNOWN_OP", "values", Collections.singletonList("Value"))
        ));
        
        queryService.getQueryResult("getAntennaReport", params);
        
        // Unknown op results in no condition added to list, so returns empty string
        // Which means no filtering clause added
        verify(session).run(anyString(), any(Map.class));
    }

    // Removed duplicate methods

    @Test
    @SuppressWarnings("unchecked")
    void getQueryResult_ThrowsExceptionOnDbError() {
        when(databaseDriver.sessionFor()).thenThrow(new RuntimeException("DB Error"));
        
        assertThrows(GraphQlApplicationException.class, () -> queryService.getQueryResult("testQuery", Collections.emptyMap()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getQueryResult_HandlesFilters_EQ_MultipleValues() {
        when(databaseDriver.sessionFor()).thenReturn(session);
        when(session.run(anyString(), any(Map.class))).thenReturn(result);
        when(result.list(any(Function.class))).thenReturn(Collections.emptyList());

        Map<String, Object> params = new HashMap<>();
        params.put("filters", Collections.singletonList(
            Map.of("field", "manufacturer_type", "op", "EQ", "values", Arrays.asList("TypeA", "TypeB"))
        ));
        
        queryService.getQueryResult("getAntennaReport", params);
        
        // Should default to IN for multiple values
        verify(session).run(eq("MATCH (obj:Antenna)  WHERE obj.manufacturerType IN $filterParam0 RETURN obj"), any(Map.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getQueryResult_HandlesFilters_IN() {
        when(databaseDriver.sessionFor()).thenReturn(session);
        when(session.run(anyString(), any(Map.class))).thenReturn(result);
        when(result.list(any(Function.class))).thenReturn(Collections.emptyList());

        Map<String, Object> params = new HashMap<>();
        params.put("filters", Collections.singletonList(
            Map.of("field", "manufacturer_type", "op", "IN", "values", Arrays.asList("TypeA", "TypeB"))
        ));
        
        queryService.getQueryResult("getAntennaReport", params);
        
        verify(session).run(org.mockito.ArgumentMatchers.contains("obj.manufacturerType IN $filterParam0"), any(Map.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getQueryResult_HandlesFilters_CONTAINS() {
        when(databaseDriver.sessionFor()).thenReturn(session);
        when(session.run(anyString(), any(Map.class))).thenReturn(result);
        when(result.list(any(Function.class))).thenReturn(Collections.emptyList());

        Map<String, Object> params = new HashMap<>();
        params.put("filters", Collections.singletonList(
            Map.of("field", "manufacturer_type", "op", "CONTAINS", "values", Collections.singletonList("Type"))
        ));
        
        queryService.getQueryResult("getAntennaReport", params);
        
        verify(session).run(anyString(), any(Map.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getQueryResult_HandlesFilters_MultipleConditions() {
        when(databaseDriver.sessionFor()).thenReturn(session);
        when(session.run(anyString(), any(Map.class))).thenReturn(result);
        when(result.list(any(Function.class))).thenReturn(Collections.emptyList());

        Map<String, Object> params = new HashMap<>();
        params.put("filters", Arrays.asList(
            Map.of("field", "manufacturer_type", "op", "EQ", "values", Collections.singletonList("TypeA")),
            Map.of("field", "manufacturer_type", "op", "CONTAINS", "values", Collections.singletonList("Type"))
        ));
        
        queryService.getQueryResult("getAntennaReport", params);
        
        verify(session).run(org.mockito.ArgumentMatchers.argThat(cypher -> {
            String c = (String) cypher;
            return c.contains("obj.manufacturerType = $filterParam0") &&
                   c.contains("AND") &&
                   c.contains("obj.manufacturerType CONTAINS $filterParam1");
        }), any(Map.class));
    }

    // Removed duplicate methods

    // Removed duplicate methods

    @Test
    @SuppressWarnings("unchecked")
    void getQueryResult_HandlesSorting() {
        when(databaseDriver.sessionFor()).thenReturn(session);
        when(session.run(anyString(), any(Map.class))).thenReturn(result);
        when(result.list(any(Function.class))).thenReturn(Collections.emptyList());

        Map<String, Object> params = new HashMap<>();
        params.put("sort", Collections.singletonList(
            Map.of("field", "manufacturer_type", "direction", "DESC")
        ));
        
        queryService.getQueryResult("getAntennaReport", params);
        
        verify(session).run(eq("MATCH (obj:Antenna)  RETURN obj ORDER BY obj.manufacturerType DESC"), any(Map.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getQueryResult_HandlesPagination() {
        when(databaseDriver.sessionFor()).thenReturn(session);
        when(session.run(anyString(), any(Map.class))).thenReturn(result);
        when(result.list(any(Function.class))).thenReturn(Collections.emptyList());

        Map<String, Object> params = new HashMap<>();
        params.put("limit", 10);
        params.put("offset", 20);
        
        queryService.getQueryResult("getAntennaReport", params);
        
        verify(session).run(eq("MATCH (obj:Antenna)  RETURN obj"), any(Map.class));
    }

}
