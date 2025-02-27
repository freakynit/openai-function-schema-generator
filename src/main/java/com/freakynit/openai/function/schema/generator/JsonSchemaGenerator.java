package com.freakynit.openai.function.schema.generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.*;
import java.util.*;

public class JsonSchemaGenerator {
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final Set<Class<?>> NATIVE_DATE_TYPES = new HashSet<>(Arrays.asList(
            java.util.Date.class,
            java.sql.Date.class,
            java.sql.Timestamp.class,
            LocalDate.class,
            LocalDateTime.class,
            LocalTime.class,
            ZonedDateTime.class,
            OffsetDateTime.class,
            OffsetTime.class,
            Instant.class
    ));

    /**
     * Generates an OpenAI function calling compatible JSON schema for the given class.
     * The output is wrapped in a container as:
     * {
     *   "type": "function",
     *   "function": {
     *     ... function-level schema here ...
     *   }
     * }
     *
     * @param clazz The class to generate the schema for.
     * @return A formatted JSON string of the schema.
     */
    public static String generate(Class<?> clazz) {
        ObjectNode functionObject = mapper.createObjectNode();

        // Use class-level annotation if available.
        SchemaInfo classAnnotation = clazz.getAnnotation(SchemaInfo.class);
        String functionName = clazz.getSimpleName();
        String functionDescription = "No description provided.";
        boolean strict = false;
        boolean additionalProperties = false;
        if (classAnnotation != null) {
            if (!classAnnotation.name().isEmpty()) {
                functionName = classAnnotation.name();
            }
            if (!classAnnotation.description().isEmpty()) {
                functionDescription = classAnnotation.description();
            }
            strict = classAnnotation.strict();
            additionalProperties = classAnnotation.additionalProperties();
        }
        functionObject.put("name", functionName);
        functionObject.put("description", functionDescription);
        // Add strict at the same level as parameters.
        functionObject.put("strict", strict);

        // Create the "parameters" object.
        ObjectNode parameters = mapper.createObjectNode();
        parameters.put("type", "object");
        ObjectNode properties = mapper.createObjectNode();
        ArrayNode requiredArray = mapper.createArrayNode();

        // Process each declared field (ignoring static ones).
        for (Field field : clazz.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                continue;
            }
            field.setAccessible(true);
            SchemaInfo fieldAnnotation = field.getAnnotation(SchemaInfo.class);
            String fieldName = field.getName();
            String fieldDescription = "";
            boolean fieldRequired = false;
            String fieldFormat = "";
            if (fieldAnnotation != null) {
                if (!fieldAnnotation.name().isEmpty()) {
                    fieldName = fieldAnnotation.name();
                }
                fieldDescription = fieldAnnotation.description();
                fieldRequired = fieldAnnotation.required();
                fieldFormat = fieldAnnotation.format();
            }
            // Generate the JSON schema for this field.
            ObjectNode fieldSchema = generateSchemaForField(field, mapper);
            if (!fieldDescription.isEmpty()) {
                fieldSchema.put("description", fieldDescription);
            }
            // If format is specified at field level, add it.
            if (!fieldFormat.isEmpty()) {
                fieldSchema.put("format", fieldFormat);
            }
            properties.set(fieldName, fieldSchema);
            if (fieldRequired) {
                requiredArray.add(fieldName);
            }
        }
        parameters.set("properties", properties);
        if (requiredArray.size() > 0) {
            parameters.set("required", requiredArray);
        }
        // Add additionalProperties (class-level) if available.
        if (classAnnotation != null) {
            parameters.put("additionalProperties", additionalProperties);
        }
        functionObject.set("parameters", parameters);

        // Wrap entire output in the container.
        ObjectNode root = mapper.createObjectNode();
        root.put("type", "function");
        root.set("function", functionObject);

        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            e.printStackTrace();
            return "{}";
        }
    }

    // Generates the JSON schema for a given field.
    private static ObjectNode generateSchemaForField(Field field, ObjectMapper mapper) {
        Class<?> type = field.getType();
        return generateSchemaForClass(type, mapper, field.getGenericType());
    }

    /**
     * Recursively builds a JSON schema for a given class (including nested types).
     *
     * @param clazz       The class for which to build the schema.
     * @param mapper      The Jackson ObjectMapper.
     * @param genericType The generic type (to help handle Collections).
     * @return An ObjectNode representing the JSON schema for the type.
     */
    private static ObjectNode generateSchemaForClass(Class<?> clazz, ObjectMapper mapper, Type genericType) {
        ObjectNode schema = mapper.createObjectNode();

        // Handle known native date/time types to avoid decomposing them.
        if (NATIVE_DATE_TYPES.contains(clazz)) {
            schema.put("type", "datetime");
            return schema;
        }

        // Handle arrays.
        if (clazz.isArray()) {
            schema.put("type", "array");
            Class<?> componentType = clazz.getComponentType();
            schema.set("items", generateSchemaForClass(componentType, mapper, componentType));
        }
        // Handle Collections.
        else if (Collection.class.isAssignableFrom(clazz)) {
            schema.put("type", "array");
            if (genericType instanceof ParameterizedType) {
                ParameterizedType pType = (ParameterizedType) genericType;
                Type[] typeArgs = pType.getActualTypeArguments();
                if (typeArgs != null && typeArgs.length == 1) {
                    Type itemType = typeArgs[0];
                    if (itemType instanceof Class<?>) {
                        schema.set("items", generateSchemaForClass((Class<?>) itemType, mapper, itemType));
                    } else if (itemType instanceof ParameterizedType) {
                        Type raw = ((ParameterizedType) itemType).getRawType();
                        if (raw instanceof Class<?>) {
                            schema.set("items", generateSchemaForClass((Class<?>) raw, mapper, itemType));
                        }
                    }
                }
            } else {
                // Default to generic object if no type information.
                ObjectNode itemSchema = mapper.createObjectNode();
                itemSchema.put("type", "object");
                schema.set("items", itemSchema);
            }
        }
        // Handle primitives, wrappers, and String.
        else if (clazz.isPrimitive() || isWrapperType(clazz) || clazz == String.class) {
            String jsonType = mapJavaTypeToJsonType(clazz);
            schema.put("type", jsonType);
        }
        // Handle enums.
        else if (clazz.isEnum()) {
            schema.put("type", "string");
            ArrayNode enumValues = mapper.createArrayNode();
            Object[] enumConstants = clazz.getEnumConstants();
            for (Object constant : enumConstants) {
                if (constant instanceof Enum) {
                    enumValues.add(((Enum<?>) constant).name());
                } else {
                    enumValues.add(constant.toString());
                }
            }
            schema.set("enum", enumValues);
        }
        // For other objects, assume an "object" type and process its fields recursively.
        else {
            schema.put("type", "object");
            ObjectNode properties = mapper.createObjectNode();
            ArrayNode requiredArray = mapper.createArrayNode();
            for (Field field : clazz.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }
                field.setAccessible(true);
                SchemaInfo fieldAnnotation = field.getAnnotation(SchemaInfo.class);
                String fieldName = field.getName();
                boolean fieldRequired = false;
                String fieldDescription = "";
                String fieldFormat = "";
                if (fieldAnnotation != null) {
                    if (!fieldAnnotation.name().isEmpty()) {
                        fieldName = fieldAnnotation.name();
                    }
                    fieldDescription = fieldAnnotation.description();
                    fieldRequired = fieldAnnotation.required();
                    fieldFormat = fieldAnnotation.format();
                }
                ObjectNode fieldSchema = generateSchemaForField(field, mapper);
                if (!fieldDescription.isEmpty()) {
                    fieldSchema.put("description", fieldDescription);
                }
                if (!fieldFormat.isEmpty()) {
                    fieldSchema.put("format", fieldFormat);
                }
                properties.set(fieldName, fieldSchema);
                if (fieldRequired) {
                    requiredArray.add(fieldName);
                }
            }
            schema.set("properties", properties);
            if (requiredArray.size() > 0) {
                schema.set("required", requiredArray);
            }
        }
        return schema;
    }

    // Overloaded helper for cases without generic type details.
    private static ObjectNode generateSchemaForClass(Class<?> clazz, ObjectMapper mapper) {
        return generateSchemaForClass(clazz, mapper, clazz);
    }

    // Checks if the class is a wrapper for a primitive type.
    private static boolean isWrapperType(Class<?> clazz) {
        return clazz.equals(Boolean.class) ||
                clazz.equals(Integer.class) ||
                clazz.equals(Character.class) ||
                clazz.equals(Byte.class) ||
                clazz.equals(Short.class) ||
                clazz.equals(Double.class) ||
                clazz.equals(Long.class) ||
                clazz.equals(Float.class) ||
                clazz.equals(BigInteger.class) ||
                clazz.equals(BigDecimal.class);
    }

    // Maps a Java type to a JSON Schema type.
    private static String mapJavaTypeToJsonType(Class<?> clazz) {
        if (clazz.equals(String.class) || clazz.equals(Character.class) || clazz.equals(char.class)) {
            return "string";
        } else if (clazz.equals(Integer.class) || clazz.equals(int.class)
                || clazz.equals(Long.class) || clazz.equals(long.class)
                || clazz.equals(Short.class) || clazz.equals(short.class)
                || clazz.equals(Byte.class) || clazz.equals(byte.class)
                || clazz.equals(BigInteger.class)) {
            return "integer";
        } else if (clazz.equals(Double.class) || clazz.equals(double.class)
                || clazz.equals(Float.class) || clazz.equals(float.class)
                || clazz.equals(BigDecimal.class)) {
            return "number";
        } else if (clazz.equals(Boolean.class) || clazz.equals(boolean.class)) {
            return "boolean";
        } else if (clazz.isArray() || Collection.class.isAssignableFrom(clazz)) {
            return "array";
        } else if (Map.class.isAssignableFrom(clazz)) {
            return "object";
        }/* else if (Date.class.isAssignableFrom(clazz) || clazz.getName().startsWith("java.time.")) {
            return "date";  // Skip date/time checks from here since we have already considered them before
        }*/ else {
            return "object";
        }
    }

}