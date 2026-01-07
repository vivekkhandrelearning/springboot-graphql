package com.example.dynamicgraphreportui.service;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SchemaBasedQueryGeneratorMethodsTest {

    @Test
    void prepareRelationshipFields_GeneratesCorrectCypher() {
        SchemaBasedQueryGenerator generator = new SchemaBasedQueryGenerator();
        StringBuilder cypher = new StringBuilder("MATCH (n:Test)");
        
        generator.prepareRelationshipFields("Test", Collections.singletonList("related"), cypher);
        
        String result = cypher.toString();
        assertTrue(result.contains("RETURN n{.*"));
        assertTrue(result.contains(", related: related} as test"));
    }
}
