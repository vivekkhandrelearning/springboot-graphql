package com.example.dynamicgraphreportui;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Value;
import org.neo4j.driver.types.Node;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

@Service
public class QueryService {

    private final Map<String, Map<String, String>> queries;
    private final Driver driver;

    public QueryService(Driver driver) {
        this.driver = driver;
        Yaml yaml = new Yaml();
        InputStream inputStream = this.getClass()
                .getClassLoader()
                .getResourceAsStream("queries.yml");
        this.queries = yaml.load(inputStream);
    }

    public Object getQueryResult(String queryName, Map<String, Object> parameters) {
        System.out.println("QueryService.getQueryResult called with queryName: " + queryName + ", parameters: " + parameters);
        try (Session session = driver.session(SessionConfig.forDatabase("neo4jmitsonly1"))) {
            String cypher = queries.get(queryName).get("cypher");
            System.out.println("Executing cypher: " + cypher);
            Result result = session.run(cypher, parameters);
            List<Map<String, Object>> list = result.list(r -> convertRecord(r));
            System.out.println("Query result: " + list);
            return list;
        } catch (Exception e) {
            System.out.println("Error executing query: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("No results found, returning null");
        return null;
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
