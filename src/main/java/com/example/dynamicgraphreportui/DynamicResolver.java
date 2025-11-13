package com.example.dynamicgraphreportui;

import java.util.Map;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Component;

@Component
public class DynamicResolver {

    private final QueryService queryService;

    public DynamicResolver(QueryService queryService) {
        this.queryService = queryService;
    }

    @SchemaMapping(typeName = "Query", field = "runQuery")
    public Object runQuery(@Argument String queryName, @Argument Map<String, Object> parameters) {
        System.out.println("DynamicResolver.runQuery called with queryName: " + queryName + ", parameters: " + parameters);
        return queryService.getQueryResult(queryName, parameters);
    }
}
