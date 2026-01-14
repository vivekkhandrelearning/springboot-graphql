package com.example.graphql.service;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.example.graphql.exceptions.GraphQlApplicationException;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
import org.neo4j.driver.types.Node;
import org.owasp.esapi.ESAPI;
import org.owasp.esapi.Logger;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.yaml.snakeyaml.Yaml;

import com.abcde.tni.commonutils.neo4j.DatabaseDriver;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class QueryService {

    private static final Logger logger = ESAPI.getLogger(QueryService.class);
    private final Map<String, Map<String, Object>> queries;
    private final DatabaseDriver databaseDriver;
    private final MetadataService metadataService;

    public QueryService(DatabaseDriver databaseDriver, MetadataService metadataService) {
        this.databaseDriver = databaseDriver;
        this.metadataService = metadataService;
        Yaml yaml = new Yaml();
        InputStream inputStream = this.getClass()
                .getClassLoader()
                .getResourceAsStream("queries.yml");
        this.queries = yaml.load(inputStream);
    }

    public Set<String> getQueryNames() {
        return queries.keySet();
    }

    public Object getQueryResult(String queryName, Map<String, Object> parameters) {
        List<Map<String, Object>> rows = new ArrayList<>();
        logger.info(Logger.EVENT_SUCCESS, "QueryService.getQueryResult called with queryName: " + queryName + ", parameters: " + parameters);
        try (Session session = databaseDriver.sessionFor()) {
            Map<String, Object> queryDefinition = queries.get(queryName);
            String cypher = (String) queryDefinition.get("cypher");
            Map<String, Object> fieldMappings = new HashMap<>();
            
            // Handle field mappings and WHERE clause placeholders
            for (int cnt = 1; cnt < 5; cnt++) {
                Map<String, Object> fieldMapping = (Map<String, Object>) queryDefinition.get("fieldMapping" + cnt);
                if (!ObjectUtils.isEmpty(fieldMapping)) {
                    // Build WHERE clause from filters
                    String whereClause = buildWhereClause(parameters, fieldMapping);
                    cypher = cypher.replace("{{WHERE_CLAUSE_" + cnt + "}}", whereClause);
                    fieldMappings.putAll(fieldMapping);
                } else {
                    // Replace unused placeholders with empty string
                    cypher = cypher.replace("{{WHERE_CLAUSE_" + cnt + "}}", "");
                }
            }

            // Handle dynamic replacements (e.g., {{label}})
            for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (value instanceof String) {
                    String placeholder = "{{" + key + "}}";
                    if (cypher.contains(placeholder)) {
                        String safeValue = ((String) value).replaceAll("[^a-zA-Z0-9_]", "");
                        cypher = cypher.replace(placeholder, safeValue);
                    }
                }
            }
            parameters.putIfAbsent("offset", 0);
            parameters.putIfAbsent("limit", 1000);
            cypher = cypher.replace("{{SKIP_LIMIT}}", " SKIP $offset LIMIT $limit");
            String orderBy = handleOrderBy(parameters, fieldMappings);
            cypher = cypher + orderBy;
            // Append Pagination
            logger.debug(Logger.EVENT_UNSPECIFIED, "Executing cypher: "+ String.format("%s  parameters %s", cypher, parameters));
            // Execute Main Query
            Result result = session.run(cypher, parameters);
            rows = result.list(this::convertRecord);
            logger.debug(Logger.EVENT_UNSPECIFIED, "Query result size: " + rows.size());
        } catch (Exception e) {
            logger.error(Logger.EVENT_FAILURE, "Error executing query: " + e.getMessage(), e);
            throw new GraphQlApplicationException("QUERY_EXECUTION_ERROR", "Error executing query: " + e.getMessage(), e);
        }
        return rows;
    }

    static String handleOrderBy(Map<String, Object> parameters, Map<String, Object> fieldMapping) {
       String orderBy = "";
        // Append ORDER BY if sort is present
        if (parameters.containsKey("sort")) {
            List<Map<String, Object>> sortList = (List<Map<String, Object>>) parameters.get("sort");
            if (sortList != null && !sortList.isEmpty() && fieldMapping != null) {
                List<String> orderBys = new ArrayList<>();
                for (Map<String, Object> sort : sortList) {
                    String field = (String) sort.get("field");
                    String direction = (String) sort.get("direction");
                    Object mapping = fieldMapping.get(field);
                    String dbField = getDbField(mapping);
                    if (dbField != null) {
                        orderBys.add(dbField + " " + (direction != null ? direction : "ASC"));
                    }
                }
                if (!orderBys.isEmpty()) {
                    orderBy = " ORDER BY " + String.join(", ", orderBys);
                }
            }
        }
        return  orderBy;
    }

    String buildWhereClause(Map<String, Object> parameters, Map<String, Object> fieldMapping) {
        List<Map<String, Object>> filters = (List<Map<String, Object>>) parameters.get("filters");
        List<String> conditions = new ArrayList<>();
        int paramCounter = 0;
        if (!ObjectUtils.isEmpty(filters)) {
            for (Map<String, Object> filter : filters) {
                String field = (String) filter.get("field");
                String op = (String) filter.get("op");
                List<String> valuesStr = (List<String>) filter.get("values");
                Object mapping = fieldMapping.get(field);
                
                String dbField = getDbField(mapping);
                if (dbField == null) continue;
                
                List<Object> values = convertValues(valuesStr, mapping);

                String paramName = "filterParam" + paramCounter++;
                parameters.put(paramName, values);

                switch (op) {
                    case "EQ":
                        if (values.size() == 1) {
                            parameters.put(paramName, values.get(0));
                            conditions.add(dbField + " = $" + paramName);
                        } else {
                            conditions.add(dbField + " IN $" + paramName);
                        }
                        break;
                    case "IN":
                        conditions.add(dbField + " IN $" + paramName);
                        break;
                    case "CONTAINS":
                        if (values.size() == 1) {
                            parameters.put(paramName, values.get(0));
                            conditions.add(dbField + " CONTAINS $" + paramName);
                        }
                        break;
                    case "GT":
                        if (values.size() == 1) {
                            parameters.put(paramName, values.get(0));
                            conditions.add(dbField + " > $" + paramName);
                        }
                        break;
                    case "LT":
                         if (values.size() == 1) {
                            parameters.put(paramName, values.get(0));
                            conditions.add(dbField + " < $" + paramName);
                        }
                        break;
                    case "NEQ":
                        if (values.size() == 1) {
                            parameters.put(paramName, values.get(0));
                            conditions.add(dbField + " <> $" + paramName);
                        }
                        break;
                    // Add other ops as needed
                    default:
                        break;
                }
            }
        }
        return conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
    }
    
    private static String getDbField(Object mapping) {
        if (mapping instanceof String) {
            return (String) mapping;
        } else if (mapping instanceof Map) {
            return (String) ((Map<?, ?>) mapping).get("dbField");
        }
        return null;
    }
    
    private static String getType(Object mapping) {
        if (mapping instanceof Map) {
            return (String) ((Map<?, ?>) mapping).get("type");
        }
        return "STRING";
    }
    
    private List<Object> convertValues(List<String> values, Object mapping) {
        if (values == null) return new ArrayList<>();
        String type = getType(mapping);
        String enumName = (mapping instanceof Map) ? (String) ((Map<?, ?>) mapping).get("enumName") : null;
        
        return values.stream().map(v -> {
            if ("ENUM".equals(type) && enumName != null) {
                Object enumValue = metadataService.getEnumValue(enumName, v);
                if (enumValue != null) {
                    return enumValue;
                }
                // Fallback to original value if not found
            }
            if ("NUMBER".equals(type) || "LONG".equals(type)) {
                try {
                    return Long.parseLong(v);
                } catch (NumberFormatException e) {
                    return v; // Fallback
                }
            } else if ("INTEGER".equals(type)) {
                 try {
                    return Integer.parseInt(v);
                } catch (NumberFormatException e) {
                    return v; // Fallback
                }
            } else if ("DATETIME".equals(type)) {
                try {
                    return OffsetDateTime.parse(v).toInstant().toEpochMilli();
                } catch (Exception e) {
                    return v; // Fallback if parsing fails
                }
            }
            return v;
        }).collect(java.util.stream.Collectors.toList());
    }

    Map<String, Object> convertRecord(Record record) {
        Map<String, Object> result = new HashMap<>();
        for (String key : record.keys()) {
            Value value = record.get(key);
            result.put(key, convertValue(value));
        }
        return result;
    }

    private Object convertValue(Value value) {
        if (value.hasType(org.neo4j.driver.internal.types.InternalTypeSystem.TYPE_SYSTEM.NODE())) {
            Node node = value.asNode();
            Map<String, Object> nodeMap = new HashMap<>();
            nodeMap.put("id", node.id());
            nodeMap.put("labels", node.labels());
            nodeMap.put("properties", node.asMap());
            return nodeMap;
        } else if (value.hasType(org.neo4j.driver.internal.types.InternalTypeSystem.TYPE_SYSTEM.RELATIONSHIP())) {
            return value.asRelationship().asMap();
        } else {
            return value.asObject();
        }
    }
}
