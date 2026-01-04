package com.example.dynamicgraphreportui;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;

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
    void getQueryResult_ExecutesCypherAndReturnsList() {
        when(databaseDriver.sessionFor()).thenReturn(session);
        when(session.run(anyString(), any(Map.class))).thenReturn(result);
        
        Map<String, Object> expectedRow = new HashMap<>();
        expectedRow.put("key", "value");
        List<Map<String, Object>> mockList = Collections.singletonList(expectedRow);
        
        when(result.list(any(Function.class))).thenReturn(mockList);

        List<Object> actual = (List<Object>) queryService.getQueryResult("testQuery", Collections.emptyMap());

        assertNotNull(actual);
        assertEquals(1, actual.size());
        assertEquals(expectedRow, actual.get(0));
        
        verify(session).run(anyString(), any(Map.class));
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
}
