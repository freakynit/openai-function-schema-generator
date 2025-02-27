package com.freakynit.openai.function.schema.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class JsonSchemaGeneratorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    // 1. Test with an empty class (no fields)
    static class EmptyClass {
    }

    @Test
    void testEmptyClass() throws Exception {
        String json = JsonSchemaGenerator.generate(EmptyClass.class);
        JsonNode root = mapper.readTree(json);

        // Check container structure.
        assertEquals("function", root.get("type").asText());
        JsonNode functionNode = root.get("function");
        assertNotNull(functionNode);

        // Default class-level values.
        assertEquals("EmptyClass", functionNode.get("name").asText());
        assertEquals("No description provided.", functionNode.get("description").asText());
        assertFalse(functionNode.get("strict").asBoolean());

        // Parameters object should have an empty properties node.
        JsonNode parameters = functionNode.get("parameters");
        assertEquals("object", parameters.get("type").asText());
        assertTrue(parameters.get("properties").isEmpty());
        assertFalse(parameters.has("required"));
    }

    // 2. Test a class with primitive and wrapper fields (with some required annotations)
    static class PrimitiveClass {
        @SchemaInfo(required = true, description = "An integer field")
        private int anInt;
        private boolean aBoolean;
        @SchemaInfo(required = true)
        private Double aDouble;
        private BigInteger bigInteger;
        private BigDecimal bigDecimal;
    }

    @Test
    void testPrimitiveClass() throws Exception {
        String json = JsonSchemaGenerator.generate(PrimitiveClass.class);
        JsonNode root = mapper.readTree(json);
        JsonNode properties = root.get("function").get("parameters").get("properties");

        // anInt should be integer with a description.
        JsonNode anIntNode = properties.get("anInt");
        assertNotNull(anIntNode);
        assertEquals("integer", anIntNode.get("type").asText());
        assertEquals("An integer field", anIntNode.get("description").asText());

        // aBoolean should be boolean.
        JsonNode aBooleanNode = properties.get("aBoolean");
        assertNotNull(aBooleanNode);
        assertEquals("boolean", aBooleanNode.get("type").asText());

        // aDouble should be number.
        JsonNode aDoubleNode = properties.get("aDouble");
        assertNotNull(aDoubleNode);
        assertEquals("number", aDoubleNode.get("type").asText());

        // bigInteger should be integer.
        JsonNode bigIntegerNode = properties.get("bigInteger");
        assertNotNull(bigIntegerNode);
        assertEquals("integer", bigIntegerNode.get("type").asText());

        // bigDecimal should be number.
        JsonNode bigDecimalNode = properties.get("bigDecimal");
        assertNotNull(bigDecimalNode);
        assertEquals("number", bigDecimalNode.get("type").asText());

        // Check required array includes only the fields marked as required.
        JsonNode required = root.get("function").get("parameters").get("required");
        assertNotNull(required);
        List<String> reqList = new ArrayList<>();
        required.forEach(node -> reqList.add(node.asText()));
        assertTrue(reqList.contains("anInt"));
        assertTrue(reqList.contains("aDouble"));
        assertFalse(reqList.contains("aBoolean"));
        assertFalse(reqList.contains("bigInteger"));
    }

    // 3. Test a class with various collection types.
    static class CollectionClass {
        private List<String> strings;
        private Set<Integer> integers;
        private String[] stringArray;
    }

    @Test
    void testCollectionClass() throws Exception {
        String json = JsonSchemaGenerator.generate(CollectionClass.class);
        JsonNode properties = mapper.readTree(json)
                .get("function").get("parameters").get("properties");

        // For List<String> -> type array with items type string.
        JsonNode stringsNode = properties.get("strings");
        assertNotNull(stringsNode);
        assertEquals("array", stringsNode.get("type").asText());
        assertEquals("string", stringsNode.get("items").get("type").asText());

        // For Set<Integer> -> type array with items type integer.
        JsonNode integersNode = properties.get("integers");
        assertNotNull(integersNode);
        assertEquals("array", integersNode.get("type").asText());
        assertEquals("integer", integersNode.get("items").get("type").asText());

        // For String[] -> type array with items type string.
        JsonNode stringArrayNode = properties.get("stringArray");
        assertNotNull(stringArrayNode);
        assertEquals("array", stringArrayNode.get("type").asText());
        assertEquals("string", stringArrayNode.get("items").get("type").asText());
    }

    // 4. Test a class with native date/time fields.
    static class DateClass {
        private LocalDate localDate;
        private Date utilDate;
    }

    @Test
    void testDateClass() throws Exception {
        String json = JsonSchemaGenerator.generate(DateClass.class);
        JsonNode properties = mapper.readTree(json)
                .get("function").get("parameters").get("properties");

        // Both fields should be mapped to type "datetime".
        JsonNode localDateNode = properties.get("localDate");
        assertNotNull(localDateNode);
        assertEquals("datetime", localDateNode.get("type").asText());

        JsonNode utilDateNode = properties.get("utilDate");
        assertNotNull(utilDateNode);
        assertEquals("datetime", utilDateNode.get("type").asText());
    }

    // 5. Test a class with an enum field.
    enum TestEnum { VALUE1, VALUE2 }

    static class EnumContainer {
        private TestEnum testEnum;
    }

    @Test
    void testEnumContainer() throws Exception {
        String json = JsonSchemaGenerator.generate(EnumContainer.class);
        JsonNode properties = mapper.readTree(json)
                .get("function").get("parameters").get("properties");

        JsonNode enumNode = properties.get("testEnum");
        assertNotNull(enumNode);
        // Enums are rendered as type "string" with an "enum" array.
        assertEquals("string", enumNode.get("type").asText());
        JsonNode enumArray = enumNode.get("enum");
        assertNotNull(enumArray);
        List<String> enumValues = new ArrayList<>();
        enumArray.forEach(n -> enumValues.add(n.asText()));
        assertTrue(enumValues.contains("VALUE1"));
        assertTrue(enumValues.contains("VALUE2"));
    }

    // 6. Test a class with a nested object.
    static class ChildClass {
        private String childField;
    }

    static class ParentClass {
        private ChildClass child;
    }

    @Test
    void testNestedObject() throws Exception {
        String json = JsonSchemaGenerator.generate(ParentClass.class);
        JsonNode childSchema = mapper.readTree(json)
                .get("function").get("parameters").get("properties").get("child");
        assertNotNull(childSchema);
        assertEquals("object", childSchema.get("type").asText());
        JsonNode childProps = childSchema.get("properties");
        assertNotNull(childProps);
        JsonNode childFieldNode = childProps.get("childField");
        assertNotNull(childFieldNode);
        assertEquals("string", childFieldNode.get("type").asText());
    }

    // 7. Test class-level SchemaInfo annotation overriding default values.
    @SchemaInfo(name = "CustomFunction", description = "Custom description", strict = true, additionalProperties = true)
    static class AnnotatedClass {
        private int field1;
    }

    @Test
    void testClassLevelAnnotation() throws Exception {
        String json = JsonSchemaGenerator.generate(AnnotatedClass.class);
        JsonNode functionNode = mapper.readTree(json).get("function");
        assertEquals("CustomFunction", functionNode.get("name").asText());
        assertEquals("Custom description", functionNode.get("description").asText());
        assertTrue(functionNode.get("strict").asBoolean());
        // additionalProperties is added at the parameters level.
        JsonNode parameters = functionNode.get("parameters");
        assertTrue(parameters.get("additionalProperties").asBoolean());
    }

    // 8. Test field-level SchemaInfo annotation overriding field name, description, format, and required.
    static class CustomFieldClass {
        @SchemaInfo(name = "customName", description = "Custom field", format = "email", required = true)
        private String originalName;
    }

    @Test
    void testFieldLevelAnnotation() throws Exception {
        String json = JsonSchemaGenerator.generate(CustomFieldClass.class);
        JsonNode properties = mapper.readTree(json)
                .get("function").get("parameters").get("properties");

        // The property key should be "customName" (not "originalName")
        JsonNode customFieldNode = properties.get("customName");
        assertNotNull(customFieldNode);
        assertEquals("string", customFieldNode.get("type").asText());
        assertEquals("Custom field", customFieldNode.get("description").asText());
        assertEquals("email", customFieldNode.get("format").asText());

        // Check that "customName" is listed in the required array.
        JsonNode required = mapper.readTree(json)
                .get("function").get("parameters").get("required");
        boolean found = false;
        for (JsonNode node : required) {
            if ("customName".equals(node.asText())) {
                found = true;
                break;
            }
        }
        assertTrue(found, "customName should be required");
    }

    // 9. Test a class with a Map field.
    static class MapClass {
        private Map<String, Integer> mapField;
    }

    @Test
    void testMapField() throws Exception {
        String json = JsonSchemaGenerator.generate(MapClass.class);
        JsonNode mapNode = mapper.readTree(json)
                .get("function").get("parameters").get("properties").get("mapField");
        assertNotNull(mapNode);
        // Maps are mapped to type "object" per our implementation.
        assertEquals("object", mapNode.get("type").asText());
    }

    // 10. Test a class with a nested collection (e.g. List<List<String>>).
    static class NestedCollectionClass {
        private List<List<String>> nested;
    }

    @Test
    void testNestedCollection() throws Exception {
        String json = JsonSchemaGenerator.generate(NestedCollectionClass.class);
        JsonNode nestedNode = mapper.readTree(json)
                .get("function").get("parameters").get("properties").get("nested");
        assertNotNull(nestedNode);
        assertEquals("array", nestedNode.get("type").asText());

        JsonNode innerArray = nestedNode.get("items");
        assertNotNull(innerArray);
        assertEquals("array", innerArray.get("type").asText());

        JsonNode innerItems = innerArray.get("items");
        assertNotNull(innerItems);
        assertEquals("string", innerItems.get("type").asText());
    }

    // 11. Test that static fields are ignored.
    static class StaticFieldClass {
        public static String staticField; // Should be ignored.
        private String normalField;
    }

    @Test
    void testStaticFieldIgnored() throws Exception {
        String json = JsonSchemaGenerator.generate(StaticFieldClass.class);
        JsonNode properties = mapper.readTree(json)
                .get("function").get("parameters").get("properties");
        assertNotNull(properties.get("normalField"));
        assertNull(properties.get("staticField"), "Static fields should be ignored in the schema");
    }
}
