package com.example.dynamicgraphreportui;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import graphql.Scalars;
import graphql.language.Field;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingFieldSelectionSet;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.SelectedField;

@ExtendWith(MockitoExtension.class)
class SchemaBasedQueryGeneratorTest {

    @Mock
    private DataFetchingEnvironment environment;
    @Mock
    private GraphQLSchema schema;
    
    private SchemaBasedQueryGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new SchemaBasedQueryGenerator();
    }

    @Test
    void generateQuery_GeneratesBasicMatchForSingleType() {
        GraphQLObjectType type = GraphQLObjectType.newObject()
                .name("Person")
                .field(GraphQLFieldDefinition.newFieldDefinition().name("name").type(Scalars.GraphQLString))
                .build();
        
        when(environment.getGraphQLSchema()).thenReturn(schema);
        when(schema.getType("Person")).thenReturn(type);
        
        Field astField = new Field("person");
        when(environment.getField()).thenReturn(astField);
        
        DataFetchingFieldSelectionSet selectionSet = mock(DataFetchingFieldSelectionSet.class);
        SelectedField selectedField = mock(SelectedField.class);
        when(selectedField.getName()).thenReturn("name");
        when(selectionSet.getFields()).thenReturn(Collections.singletonList(selectedField));
        when(environment.getSelectionSet()).thenReturn(selectionSet);

        String cypher = generator.generateQuery(environment);

        assertTrue(cypher.contains("MATCH (n:Person)"));
        assertTrue(cypher.contains("RETURN n{name: n.name}"));
    }
}
