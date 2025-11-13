package com.example.dynamicgraphreportui;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.SelectedField;

@Component
public class SchemaBasedQueryGenerator {

    public String generateQuery(DataFetchingEnvironment environment) {
        String fieldName = environment.getField().getName();
        GraphQLSchema schema = environment.getGraphQLSchema();
        
        // Determine if this is a single or list query
        boolean isList = fieldName.endsWith("s");
        String typeName = getTypeNameFromField(fieldName, isList);
        
        // Get the GraphQL type definition
        GraphQLType type = schema.getType(typeName);
        if (!(type instanceof GraphQLObjectType)) {
            throw new RuntimeException("Type " + typeName + " not found in schema");
        }
        
        GraphQLObjectType objectType = (GraphQLObjectType) type;
        
        if (isList) {
            return generateListQuery(typeName, objectType, environment);
        } else {
            return generateSingleQuery(typeName, objectType, environment);
        }
    }
    
    private String generateListQuery(String typeName, GraphQLObjectType objectType, DataFetchingEnvironment environment) {
        StringBuilder cypher = new StringBuilder();
        cypher.append("MATCH (n:").append(typeName).append(")");
        
        // Get requested relationship fields from GraphQL selection
        List<String> relationshipFields = getRelationshipFields(environment, objectType);
        
        // Add OPTIONAL MATCH for each relationship
        for (String relField : relationshipFields) {
            GraphQLFieldDefinition fieldDef = objectType.getFieldDefinition(relField);
            String relatedTypeName = getRelatedTypeName(fieldDef);
            String relationshipName = deriveRelationshipName(typeName, relField, relatedTypeName);
            
            cypher.append(" OPTIONAL MATCH ");
            if (isCollectionField(fieldDef)) {
                // Incoming relationship (e.g., Shelf -> devices)
                cypher.append("(").append(relField.toLowerCase()).append(":").append(relatedTypeName)
                      .append(")-[:").append(relationshipName).append("]->(n)");
            } else {
                // Outgoing relationship (e.g., Device -> shelf)
                cypher.append("(n)-[:").append(relationshipName).append("]->(")
                      .append(relField.toLowerCase()).append(":").append(relatedTypeName).append(")");
            }
        }
        
        // Add WITH clause for proper grouping when using collect()
        boolean hasCollections = relationshipFields.stream()
                .anyMatch(relField -> isCollectionField(objectType.getFieldDefinition(relField)));
        
        if (hasCollections) {
            cypher.append(" WITH n");
            for (String relField : relationshipFields) {
                GraphQLFieldDefinition fieldDef = objectType.getFieldDefinition(relField);
                if (isCollectionField(fieldDef)) {
                    cypher.append(", collect(").append(relField.toLowerCase()).append(") as ").append(relField);
                } else {
                    cypher.append(", ").append(relField.toLowerCase()).append(" as ").append(relField);
                }
            }
            cypher.append(" RETURN n{.*");
            for (String relField : relationshipFields) {
                cypher.append(", ").append(relField).append(": ").append(relField);
            }
            cypher.append("} as ").append(typeName.toLowerCase());
        } else if (!relationshipFields.isEmpty()) {
            cypher.append(" RETURN n{.*");
            for (String relField : relationshipFields) {
                cypher.append(", ").append(relField).append(": ").append(relField.toLowerCase());
            }
            cypher.append("} as ").append(typeName.toLowerCase());
        } else {
            // Get all requested fields from GraphQL selection
            List<String> requestedFields = environment.getSelectionSet().getFields().stream()
                    .map(SelectedField::getName)
                    .collect(Collectors.toList());
            
            if (requestedFields.isEmpty()) {
                cypher.append(" RETURN n as ").append(typeName.toLowerCase());
            } else {
                cypher.append(" RETURN n{");
                for (int i = 0; i < requestedFields.size(); i++) {
                    if (i > 0) cypher.append(", ");
                    String field = requestedFields.get(i);
                    cypher.append(field).append(": n.").append(field);
                }
                cypher.append("} as ").append(typeName.toLowerCase());
            }
        }
        cypher.append(" LIMIT 10 ");
        return cypher.toString();
    }
    
    private String generateSingleQuery(String typeName, GraphQLObjectType objectType, DataFetchingEnvironment environment) {
        StringBuilder cypher = new StringBuilder();
        cypher.append("MATCH (n:").append(typeName).append(") WHERE n.id = $id");
        
        // Get requested relationship fields from GraphQL selection
        List<String> relationshipFields = getRelationshipFields(environment, objectType);
        
        // Add OPTIONAL MATCH for each relationship
        for (String relField : relationshipFields) {
            GraphQLFieldDefinition fieldDef = objectType.getFieldDefinition(relField);
            String relatedTypeName = getRelatedTypeName(fieldDef);
            String relationshipName = deriveRelationshipName(typeName, relField, relatedTypeName);
            
            cypher.append(" OPTIONAL MATCH ");
            if (isCollectionField(fieldDef)) {
                // Incoming relationship
                cypher.append("(").append(relField.toLowerCase()).append(":").append(relatedTypeName)
                      .append(")-[:").append(relationshipName).append("]->(n)");
            } else {
                // Outgoing relationship
                cypher.append("(n)-[:").append(relationshipName).append("]->(")
                      .append(relField.toLowerCase()).append(":").append(relatedTypeName).append(")");
            }
        }
        
        // Add WITH clause for proper grouping when using collect()
        boolean hasCollections = relationshipFields.stream()
                .anyMatch(relField -> isCollectionField(objectType.getFieldDefinition(relField)));
        
        if (hasCollections) {
            cypher.append(" WITH n");
            for (String relField : relationshipFields) {
                GraphQLFieldDefinition fieldDef = objectType.getFieldDefinition(relField);
                if (isCollectionField(fieldDef)) {
                    cypher.append(", collect(").append(relField.toLowerCase()).append(") as ").append(relField);
                } else {
                    cypher.append(", ").append(relField.toLowerCase()).append(" as ").append(relField);
                }
            }
            cypher.append(" RETURN n{.*");
            for (String relField : relationshipFields) {
                cypher.append(", ").append(relField).append(": ").append(relField);
            }
            cypher.append("} as ").append(typeName.toLowerCase());
        } else if (!relationshipFields.isEmpty()) {
            cypher.append(" RETURN n{.*");
            for (String relField : relationshipFields) {
                cypher.append(", ").append(relField).append(": ").append(relField.toLowerCase());
            }
            cypher.append("} as ").append(typeName.toLowerCase());
        } else {
            // Get all requested fields from GraphQL selection
            List<String> requestedFields = environment.getSelectionSet().getFields().stream()
                    .map(SelectedField::getName)
                    .collect(Collectors.toList());
            
            if (requestedFields.isEmpty()) {
                cypher.append(" RETURN n as ").append(typeName.toLowerCase());
            } else {
                cypher.append(" RETURN n{");
                for (int i = 0; i < requestedFields.size(); i++) {
                    if (i > 0) cypher.append(", ");
                    String field = requestedFields.get(i);
                    cypher.append(field).append(": n.").append(field);
                }
                cypher.append("} as ").append(typeName.toLowerCase());
            }
        }
        cypher.append(" LIMIT 10 ");
        return cypher.toString();
    }
    
    private String getTypeNameFromField(String fieldName, boolean isList) {
        if (isList) {
            // Handle pluralization properly
            String singular;
            if (fieldName.equals("shelves")) {
                singular = "shelf";
            } else if (fieldName.endsWith("ies")) {
                // cities -> city
                singular = fieldName.substring(0, fieldName.length() - 3) + "y";
            } else if (fieldName.endsWith("s")) {
                // devices -> device
                singular = fieldName.substring(0, fieldName.length() - 1);
            } else {
                singular = fieldName;
            }
            return capitalize(singular);
        } else {
            // Just capitalize (device -> Device)
            return capitalize(fieldName);
        }
    }
    
    private List<String> getRelationshipFields(DataFetchingEnvironment environment, GraphQLObjectType objectType) {
        return environment.getSelectionSet().getFields().stream()
                .map(SelectedField::getName)
                .filter(fieldName -> !isPrimitiveField(fieldName))
                .filter(fieldName -> objectType.getFieldDefinition(fieldName) != null)
                .collect(Collectors.toList());
    }
    
    private boolean isPrimitiveField(String fieldName) {
        return fieldName.equals("id") || fieldName.equals("name") || fieldName.equals("type");
    }
    
    private String getRelatedTypeName(GraphQLFieldDefinition fieldDef) {
        // Get the actual type name from the GraphQL type
        GraphQLType type = fieldDef.getType();
        
        // Unwrap list and non-null wrappers to get the actual type
        while (type instanceof GraphQLNonNull || type instanceof GraphQLList) {
            if (type instanceof GraphQLNonNull) {
                type = ((GraphQLNonNull) type).getWrappedType();
            } else if (type instanceof GraphQLList) {
                type = ((GraphQLList) type).getWrappedType();
            }
        }
        
        if (type instanceof GraphQLObjectType) {
            return ((GraphQLObjectType) type).getName();
        }
        
        // Fallback to string parsing if needed
        String typeName = type.toString();
        if (typeName.contains("{")) {
            // Extract just the name from GraphQLObjectType{name='Shelf'...}
            int nameStart = typeName.indexOf("name='") + 6;
            int nameEnd = typeName.indexOf("'", nameStart);
            if (nameStart > 5 && nameEnd > nameStart) {
                return typeName.substring(nameStart, nameEnd);
            }
        }
        
        return typeName.replaceAll("[\\[\\]!]", "");
    }
    
    private boolean isCollectionField(GraphQLFieldDefinition fieldDef) {
        return fieldDef.getType().toString().contains("[");
    }
    
    private String deriveRelationshipName(String fromType, String fieldName, String toType) {
        // Generic relationship naming convention
        // Could be made configurable via properties or annotations
        return fromType.toUpperCase() + "_TO_" + toType.toUpperCase();
    }
    
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
