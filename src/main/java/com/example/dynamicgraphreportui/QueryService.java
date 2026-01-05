package com.example.dynamicgraphreportui;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
import org.neo4j.driver.types.Node;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import com.abcde.tni.commonutils.neo4j.DatabaseDriver;

@Service
public class QueryService {

    private final Map<String, Map<String, Object>> queries;
    private final DatabaseDriver databaseDriver;

    public QueryService(DatabaseDriver databaseDriver) {
        this.databaseDriver = databaseDriver;
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
        System.out.println("QueryService.getQueryResult called with queryName: " + queryName + ", parameters: " + parameters);
        try (Session session = databaseDriver.sessionFor()) {
            Map<String, Object> queryDefinition = queries.get(queryName);
            String cypher = (String) queryDefinition.get("cypher");
            Map<String, String> fieldMapping = (Map<String, String>) queryDefinition.get("fieldMapping");

            // Build WHERE clause from filters
            String whereClause = buildWhereClause(parameters, fieldMapping);
            cypher = cypher.replace("{{WHERE_CLAUSE}}", whereClause);

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

            // Append ORDER BY if sort is present
            if (parameters.containsKey("sort")) {
                List<Map<String, Object>> sortList = (List<Map<String, Object>>) parameters.get("sort");
                if (sortList != null && !sortList.isEmpty() && fieldMapping != null) {
                    List<String> orderBys = new ArrayList<>();
                    for (Map<String, Object> sort : sortList) {
                        String field = (String) sort.get("field");
                        String direction = (String) sort.get("direction");
                        String dbField = fieldMapping.get(field);
                        if (dbField != null) {
                            orderBys.add(dbField + " " + (direction != null ? direction : "ASC"));
                        }
                    }
                    if (!orderBys.isEmpty()) {
                        cypher += " ORDER BY " + String.join(", ", orderBys);
                    }
                }
            }

            // Append Pagination
            if (parameters.containsKey("limit") && parameters.containsKey("offset")) {
                cypher += " SKIP $offset LIMIT $limit";
            } else if (parameters.containsKey("pagination")) {
                Map<String, Object> pagination = (Map<String, Object>) parameters.get("pagination");
                int page = (int) pagination.getOrDefault("page", 0);
                int pageSize = (int) pagination.getOrDefault("pageSize", 10);
                parameters.put("offset", page * pageSize);
                parameters.put("limit", pageSize);
                cypher += " SKIP $offset LIMIT $limit";
            }

            // Handle legacy pagination params
            if (parameters.containsKey("page") && parameters.containsKey("limit") && !parameters.containsKey("offset")) {
                 int page = (int) parameters.get("page");
                 int limit = (int) parameters.get("limit");
                 parameters.put("offset", page * limit);
            }

            System.out.println("Executing cypher: " + cypher);
            
            // Execute Main Query
            Result result = session.run(cypher, parameters);
            List<Map<String, Object>> rows = result.list(this::convertRecord);
            
            // If it's a 'mitsFullReport' type query, return wrapped result
            if (parameters.containsKey("pagination")) {
                 Map<String, Object> pagination = (Map<String, Object>) parameters.get("pagination");
                 int page = (int) pagination.getOrDefault("page", 0);
                 int pageSize = (int) pagination.getOrDefault("pageSize", 10);
                 
                 Map<String, Object> response = new HashMap<>();
                 response.put("rows", rows);
                 
                 Map<String, Object> pageInfo = new HashMap<>();
                 pageInfo.put("page", page);
                 pageInfo.put("pageSize", pageSize);
                 // Note: Total items is not calculated in this implementation as requested
                 pageInfo.put("totalItems", 0); 
                 pageInfo.put("totalPages", 0);
                 
                 response.put("pageInfo", pageInfo);
                 return response;
            }

            System.out.println("Query result size: " + rows.size());
            return rows;
        } catch (Exception e) {
            System.out.println("Error executing query: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    private String buildWhereClause(Map<String, Object> parameters, Map<String, String> fieldMapping) {
        if (!parameters.containsKey("filters") || fieldMapping == null) {
            return "1=1"; // Default true condition
        }

        List<Map<String, Object>> filters = (List<Map<String, Object>>) parameters.get("filters");
        if (filters == null || filters.isEmpty()) {
            return "1=1";
        }

        List<String> conditions = new ArrayList<>();
        int paramCounter = 0;

        for (Map<String, Object> filter : filters) {
            String field = (String) filter.get("field");
            String op = (String) filter.get("op");
            List<String> values = (List<String>) filter.get("values");
            String dbField = fieldMapping.get(field);

            if (dbField == null) continue;

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
                // Add other ops as needed
                default:
                    break;
            }
        }

        return conditions.isEmpty() ? "1=1" : String.join(" AND ", conditions);
    }

    private Map<String, Object> convertRecord(Record record) {
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
