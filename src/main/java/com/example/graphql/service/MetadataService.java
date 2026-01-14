package com.example.graphql.service;

import java.util.HashMap;
import java.util.Map;

import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.owasp.esapi.ESAPI;
import org.owasp.esapi.Logger;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.telstra.tni.commonutils.neo4j.DatabaseDriver;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class MetadataService {

    private static final Logger logger = ESAPI.getLogger(MetadataService.class);
    private final DatabaseDriver databaseDriver;
    private Map<String, Map<String, Object>> enumCache = new HashMap<>();

    public MetadataService(DatabaseDriver databaseDriver) {
        this.databaseDriver = databaseDriver;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadEnums() {
        String cypher = "MATCH (n:`_Enumeration`)-[:HAS_VALUE]-(e:_EnumerationLiteral) " +
                "WITH n.name AS enumName, collect([e.name, e.value]) AS pairs " +
                "WITH collect([enumName, apoc.map.fromPairs(pairs)]) AS enumPairs " +
                "RETURN apoc.map.fromPairs(enumPairs) AS result";
        
        try (Session session = databaseDriver.sessionFor()) {
            Result result = session.run(cypher);
            if (result.hasNext()) {
                Record record = result.next();
                Map<String, Object> resultMap = record.get("result").asMap();
                for (Map.Entry<String, Object> entry : resultMap.entrySet()) {
                     enumCache.put(entry.getKey(), (Map<String, Object>) entry.getValue());
                }
                logger.info(Logger.EVENT_SUCCESS, "Loaded enums: " + enumCache.keySet());
            }
        } catch (Exception e) {
            logger.error(Logger.EVENT_FAILURE, "Failed to load enums: " + e.getMessage(), e);
        }
    }

    public Object getEnumValue(String enumName, String literalName) {
        if (enumCache.containsKey(enumName)) {
            Map<String, Object> literals = enumCache.get(enumName);
            if (literals != null && literals.containsKey(literalName)) {
                return literals.get(literalName);
            }
        }
        return null;
    }
}
