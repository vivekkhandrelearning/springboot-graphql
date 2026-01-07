package com.example.dynamicgraphreportui.service;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.dynamicgraphreportui.exceptions.GraphQlApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
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
import graphql.schema.GraphQLType;
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

    @Test
    void generateQuery_GeneratesListQueryWithArguments() {
        GraphQLObjectType type = GraphQLObjectType.newObject()
                .name("Person")
                .field(GraphQLFieldDefinition.newFieldDefinition().name("name").type(Scalars.GraphQLString))
                .field(GraphQLFieldDefinition.newFieldDefinition().name("age").type(Scalars.GraphQLInt))
                .build();

        lenient().when(environment.getGraphQLSchema()).thenReturn(schema);
        lenient().when(schema.getType("Person")).thenReturn(type);

        Field astField = new Field("persons");
        lenient().when(environment.getField()).thenReturn(astField);
        lenient().when(environment.getArguments()).thenReturn(Collections.singletonMap("name", "John"));

        DataFetchingFieldSelectionSet selectionSet = mock(DataFetchingFieldSelectionSet.class);
        SelectedField nameField = mock(SelectedField.class);
        lenient().when(nameField.getName()).thenReturn("name");

        lenient().when(selectionSet.getFields()).thenReturn(Collections.singletonList(nameField));
        lenient().when(environment.getSelectionSet()).thenReturn(selectionSet);

        String cypher = generator.generateQuery(environment);

        assertTrue(cypher.contains("MATCH (n:Person)"));
        assertTrue(cypher.contains("WHERE n.name = $name"));
        assertTrue(cypher.contains("RETURN n{name: n.name}"));
        assertTrue(cypher.contains("LIMIT 10"));
    }

    @Test
    void generateQuery_GeneratesQueryWithRelationships() {
        GraphQLObjectType addressType = GraphQLObjectType.newObject()
                .name("Address")
                .field(GraphQLFieldDefinition.newFieldDefinition().name("city").type(Scalars.GraphQLString))
                .build();

        GraphQLObjectType personType = GraphQLObjectType.newObject()
                .name("Person")
                .field(GraphQLFieldDefinition.newFieldDefinition().name("name").type(Scalars.GraphQLString))
                .field(GraphQLFieldDefinition.newFieldDefinition().name("address").type(addressType))
                .build();
        
        when(environment.getGraphQLSchema()).thenReturn(schema);
        when(schema.getType("Person")).thenReturn(personType);
        
        Field astField = new Field("person");
        when(environment.getField()).thenReturn(astField);
        
        DataFetchingFieldSelectionSet selectionSet = mock(DataFetchingFieldSelectionSet.class);
        SelectedField nameField = mock(SelectedField.class);
        when(nameField.getName()).thenReturn("name");
        SelectedField addressField = mock(SelectedField.class);
        when(addressField.getName()).thenReturn("address");
        
        when(selectionSet.getFields()).thenReturn(java.util.Arrays.asList(nameField, addressField));
        when(environment.getSelectionSet()).thenReturn(selectionSet);

        String cypher = generator.generateQuery(environment);

//        assertTrue(cypher.contains("OPTIONAL MATCH (n)-[:PERSON_TO_ADDRESS]->(address:Address)"));
        assertTrue(cypher.contains("RETURN n{.*"));
        assertTrue(cypher.contains("address: address"));
    }

    @Test
    void generateSingleQuery_GeneratesQueryWithCollectionRelationship() {
        GraphQLObjectType bookType = GraphQLObjectType.newObject()
                .name("Book")
                .field(GraphQLFieldDefinition.newFieldDefinition().name("title").type(Scalars.GraphQLString))
                .build();

        GraphQLObjectType libraryType = GraphQLObjectType.newObject()
                .name("Library")
                .field(GraphQLFieldDefinition.newFieldDefinition().name("name").type(Scalars.GraphQLString))
                .field(GraphQLFieldDefinition.newFieldDefinition().name("books").type(new graphql.schema.GraphQLList(bookType)))
                .build();
        
        when(environment.getGraphQLSchema()).thenReturn(schema);
        when(schema.getType("Library")).thenReturn(libraryType);
        
        Field astField = new Field("library"); // Singular
        when(environment.getField()).thenReturn(astField);
        
        DataFetchingFieldSelectionSet selectionSet = mock(DataFetchingFieldSelectionSet.class);
        SelectedField nameField = mock(SelectedField.class);
        when(nameField.getName()).thenReturn("name");
        SelectedField booksField = mock(SelectedField.class);
        when(booksField.getName()).thenReturn("books");
        
        when(selectionSet.getFields()).thenReturn(java.util.Arrays.asList(nameField, booksField));
        when(environment.getSelectionSet()).thenReturn(selectionSet);

        String cypher = generator.generateQuery(environment);

        assertTrue(cypher.contains("MATCH (n:Library) WHERE n.id = $id"));
        assertTrue(cypher.contains("OPTIONAL MATCH (books:Book)-[:LIBRARY_TO_BOOK]->(n)"));
        assertTrue(cypher.contains("WITH n, collect(books) as books"));
        assertTrue(cypher.contains("RETURN n{.*, books: books}"));
    }

    @Test
    void generateSingleQuery_GeneratesQueryWithSingleRelationship() {
         GraphQLObjectType addressType = GraphQLObjectType.newObject()
                .name("Address")
                .field(GraphQLFieldDefinition.newFieldDefinition().name("city").type(Scalars.GraphQLString))
                .build();

        GraphQLObjectType personType = GraphQLObjectType.newObject()
                .name("Person")
                .field(GraphQLFieldDefinition.newFieldDefinition().name("name").type(Scalars.GraphQLString))
                .field(GraphQLFieldDefinition.newFieldDefinition().name("address").type(addressType))
                .build();
        
        when(environment.getGraphQLSchema()).thenReturn(schema);
        when(schema.getType("Person")).thenReturn(personType);
        
        Field astField = new Field("person"); // Singular
        when(environment.getField()).thenReturn(astField);
        
        DataFetchingFieldSelectionSet selectionSet = mock(DataFetchingFieldSelectionSet.class);
        SelectedField nameField = mock(SelectedField.class);
        when(nameField.getName()).thenReturn("name");
        SelectedField addressField = mock(SelectedField.class);
        when(addressField.getName()).thenReturn("address");
        
        when(selectionSet.getFields()).thenReturn(java.util.Arrays.asList(nameField, addressField));
        when(environment.getSelectionSet()).thenReturn(selectionSet);

        String cypher = generator.generateQuery(environment);

        assertTrue(cypher.contains("MATCH (n:Person) WHERE n.id = $id"));
//        assertTrue(cypher.contains("OPTIONAL MATCH (n)-[:PERSON_TO_ADDRESS]->(address:Address)"));
        assertTrue(cypher.contains("RETURN n{.*"));
        assertTrue(cypher.contains("address: address"));
    }

    @Test
    void generateQuery_GeneratesBasicMatchForSingleTypeSchemaNotOfGraphQLObjectType() {
        GraphQLType graphQLType = mock(GraphQLType.class);
        when(environment.getGraphQLSchema()).thenReturn(schema);
        when(schema.getType("Person")).thenReturn(graphQLType);
        Field astField = new Field("person");
        when(environment.getField()).thenReturn(astField);
        assertThrows(GraphQlApplicationException.class, () -> generator.generateQuery(environment));
    }
}
