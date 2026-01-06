package com.example.dynamicgraphreportui;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.Mock;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Relationship;

import com.telstra.tni.commonutils.neo4j.DatabaseDriver;

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

        List<Object> actual = (List<Object>) queryService.getQueryResult("testQuery", Collections.emptyMap());

        assertNotNull(actual);
        assertEquals(1, actual.size());
        Map<String, Object> row = (Map<String, Object>) actual.get(0);
        assertEquals("value", row.get("key"));
        
        verify(session).run(anyString(), any(Map.class));
    }
    
    @Test
    @SuppressWarnings("unchecked")
    void getQueryResult_ConvertsNodeValue() {
        when(databaseDriver.sessionFor()).thenReturn(session);
        when(session.run(anyString(), any(Map.class))).thenReturn(result);

        Record record = mock(Record.class);
        Value value = mock(Value.class);
        Node node = mock(Node.class);

        when(record.keys()).thenReturn(Collections.singletonList("node"));
        when(record.get("node")).thenReturn(value);
        
        // Setup node type check
        when(value.hasType(org.neo4j.driver.internal.types.InternalTypeSystem.TYPE_SYSTEM.NODE())).thenReturn(true);
        when(value.asNode()).thenReturn(node);
        
        when(node.id()).thenReturn(123L);
        when(node.labels()).thenReturn(Collections.singletonList("Label"));
        when(node.asMap()).thenReturn(Collections.singletonMap("prop", "val"));

        when(result.list(any(Function.class))).thenAnswer(invocation -> {
            Function<Record, Map<String, Object>> mapper = invocation.getArgument(0);
            return Collections.singletonList(mapper.apply(record));
        });

        List<Object> actual = (List<Object>) queryService.getQueryResult("testQuery", Collections.emptyMap());
        Map<String, Object> row = (Map<String, Object>) actual.get(0);
        Map<String, Object> nodeMap = (Map<String, Object>) row.get("node");
        
        assertEquals(123L, nodeMap.get("id"));
        assertEquals(Collections.singletonList("Label"), nodeMap.get("labels"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getQueryResult_ConvertsRelationshipValue() {
        when(databaseDriver.sessionFor()).thenReturn(session);
        when(session.run(anyString(), any(Map.class))).thenReturn(result);

        Record record = mock(Record.class);
        Value value = mock(Value.class);
        Relationship rel = mock(Relationship.class);

        when(record.keys()).thenReturn(Collections.singletonList("rel"));
        when(record.get("rel")).thenReturn(value);
        
        // Setup relationship type check
        when(value.hasType(org.neo4j.driver.internal.types.InternalTypeSystem.TYPE_SYSTEM.NODE())).thenReturn(false);
        when(value.hasType(org.neo4j.driver.internal.types.InternalTypeSystem.TYPE_SYSTEM.RELATIONSHIP())).thenReturn(true);
        when(value.asRelationship()).thenReturn(rel);
        when(rel.asMap()).thenReturn(Collections.singletonMap("relProp", "relVal"));

        when(result.list(any(Function.class))).thenAnswer(invocation -> {
            Function<Record, Map<String, Object>> mapper = invocation.getArgument(0);
            return Collections.singletonList(mapper.apply(record));
        });

        List<Object> actual = (List<Object>) queryService.getQueryResult("testQuery", Collections.emptyMap());
        Map<String, Object> row = (Map<String, Object>) actual.get(0);
        Map<String, Object> relMap = (Map<String, Object>) row.get("rel");
        
        assertEquals("relVal", relMap.get("relProp"));
    }
    
    @Test
    @SuppressWarnings("unchecked")
    void getQueryResult_SubstitutesParameters() {
        when(databaseDriver.sessionFor()).thenReturn(session);
        when(session.run(anyString(), any(Map.class))).thenReturn(result);
        when(result.list(any(Function.class))).thenReturn(Collections.emptyList());

        Map<String, Object> params = new HashMap<>();
        params.put("name", "John");
        
        queryService.getQueryResult("parameterizedQuery", params);
        
        verify(session).run(org.mockito.ArgumentMatchers.contains("John"), any(Map.class));
    }

    @Test
    void getQueryNames_ReturnsKeys() {
        assertNotNull(queryService.getQueryNames());
        assertTrue(queryService.getQueryNames().contains("testQuery"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getQueryResult_HandlesFilters_EQ_SingleValue() {
        when(databaseDriver.sessionFor()).thenReturn(session);
        when(session.run(anyString(), any(Map.class))).thenReturn(result);
        when(result.list(any(Function.class))).thenReturn(Collections.emptyList());

        Map<String, Object> params = new HashMap<>();
        params.put("filters", Collections.singletonList(
            Map.of("field", "manufacturer_type", "op", "EQ", "values", Collections.singletonList("TypeA"))
        ));
        
        queryService.getQueryResult("getAntennaReport", params);
        
        verify(session).run(org.mockito.ArgumentMatchers.contains("obj.manufacturerType = $filterParam0"), any(Map.class));
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
        verify(session).run(org.mockito.ArgumentMatchers.contains("obj.manufacturerType IN $filterParam0"), any(Map.class));
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
        
        verify(session).run(org.mockito.ArgumentMatchers.contains("obj.manufacturerType CONTAINS $filterParam0"), any(Map.class));
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
        
        verify(session).run(org.mockito.ArgumentMatchers.contains("1=1"), any(Map.class));
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
        
        verify(session).run(org.mockito.ArgumentMatchers.contains("1=1"), any(Map.class));
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
        
        verify(session).run(org.mockito.ArgumentMatchers.contains("1=1"), any(Map.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getQueryResult_HandlesFilters_NullFieldMapping() {
        when(databaseDriver.sessionFor()).thenReturn(session);
        when(session.run(anyString(), any(Map.class))).thenReturn(result);
        when(result.list(any(Function.class))).thenReturn(Collections.emptyList());
        Map<String, Object> params = new HashMap<>();
        params.put("filters", Collections.singletonList(
            Map.of("field", "someField", "op", "EQ", "values", Collections.singletonList("Value"))
        ));

        queryService.getQueryResult("testQuery", params);
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
        
        // switch default break -> no condition added -> returns 1=1
        verify(session).run(org.mockito.ArgumentMatchers.contains("1=1"), any(Map.class));
    }

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
        
        verify(session).run(org.mockito.ArgumentMatchers.contains("ORDER BY obj.manufacturerType DESC"), any(Map.class));
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
        
        verify(session).run(org.mockito.ArgumentMatchers.contains("SKIP $offset LIMIT $limit"), any(Map.class));
    }

}
