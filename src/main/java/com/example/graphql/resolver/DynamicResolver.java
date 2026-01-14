package com.example.graphql.resolver;

import java.util.Map;

import com.example.graphql.service.QueryService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class DynamicResolver {

    private final QueryService queryService;

    public DynamicResolver(QueryService queryService) {
        this.queryService = queryService;
    }

    @SchemaMapping(typeName = "Query", field = "runQuery")
    public Object runQuery(@Argument String queryName, @Argument Map<String, Object> parameters) {
        log.debug("DynamicResolver.runQuery called with queryName: {}, parameters: {}", queryName, parameters);
        return queryService.getQueryResult(queryName, parameters);
    }
}
