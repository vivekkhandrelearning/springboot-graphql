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
import org.owasp.esapi.ESAPI;
import org.owasp.esapi.Logger;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import com.abcde.tni.commonutils.neo4j.DatabaseDriver;

@Service
@Slf4j
public class QueryService {

    private static final Logger logger = ESAPI.getLogger(QueryService.class);
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
        List<Map<String, Object>> rows = new ArrayList<>();
        logger.info(Logger.EVENT_SUCCESS, "QueryService.getQueryResult called with queryName: " + queryName + ", parameters: " + parameters);
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
            StringBuilder cypherBuilder = new StringBuilder(cypher);
            handleOrderBy(parameters, fieldMapping, cypherBuilder);

            // Append Pagination
            handlePagination(parameters, cypherBuilder);
            logger.info(Logger.EVENT_SUCCESS, "Executing cypher: " + cypherBuilder);
            
            // Execute Main Query
            Result result = session.run(cypherBuilder.toString(), parameters);
            rows = result.list(this::convertRecord);
            logger.info(Logger.EVENT_SUCCESS, "Query result size: " + rows.size());
        } catch (Exception e) {
            logger.error(Logger.EVENT_FAILURE, "Error executing query: " + e.getMessage(), e);
        }
        return rows;
    }

    static void handlePagination(Map<String, Object> parameters, StringBuilder cypherBuilder) {
        if (parameters.containsKey("limit") && parameters.containsKey("offset")) {
            cypherBuilder.append( " SKIP $offset LIMIT $limit");
        }
    }

    static void handleOrderBy(Map<String, Object> parameters, Map<String, String> fieldMapping, StringBuilder cypherBuilder) {
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
                    cypherBuilder.append( " ORDER BY " + String.join(", ", orderBys));
                }
            }
        }
    }

    String buildWhereClause(Map<String, Object> parameters, Map<String, String> fieldMapping) {
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
