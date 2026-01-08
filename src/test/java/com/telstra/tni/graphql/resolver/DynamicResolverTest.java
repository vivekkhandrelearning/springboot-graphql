package com.telstra.tni.graphql.resolver;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.telstra.tni.graphql.service.QueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DynamicResolverTest {

    @Mock
    private QueryService queryService;

    private DynamicResolver dynamicResolver;

    @BeforeEach
    void setUp() {
        dynamicResolver = new DynamicResolver(queryService);
    }

    @Test
    void runQuery_DelegatesToQueryService() {
        String queryName = "testQuery";
        Map<String, Object> params = Collections.singletonMap("param", "value");
        Object expectedResult = "result";

        when(queryService.getQueryResult(queryName, params)).thenReturn(expectedResult);

        Object result = dynamicResolver.runQuery(queryName, params);

        assertEquals(expectedResult, result);
        verify(queryService).getQueryResult(queryName, params);
    }
}
