# JSON Schema Generator

A Java library that generates OpenAI function calling compatible JSON schemas from Java classes. This library introspects Java classes (including nested objects, collections, enums, and more) and produces a structured JSON schema. The generated schema is designed to work seamlessly with OpenAI's function calling mechanism.

---

## Features

- **Automatic Schema Generation:** Converts Java class definitions into JSON schemas.
- **Supports Annotations:** Use `@SchemaInfo` annotations at both class and field levels to override default schema values (e.g., custom names, descriptions, formats, and required fields).
- **Handles Various Data Types:**
    - Primitives and their wrapper classes.
    - Collections (e.g., `List`, `Set`) and arrays.
    - Date and time types (e.g., `LocalDate`, `Date`).
    - Nested objects and nested collections.
    - Enums with auto-generated enum values.
    - Maps (rendered as objects).
- **Customizable:** Easily extend or modify the schema generation logic to fit your project's needs.

---

## Getting Started

### Installation

Simply include the [JsonSchemaGenerator.java](src/main/java/com/freakynit/openai/function/schema/generator/JsonSchemaGenerator.java) file in your source tree. You may also compile it into a JAR file and add it as a dependency.

If you are using Maven, add the necessary dependencies in your `pom.xml`. For Gradle, update your `build.gradle` accordingly.

This only has `jackson-databind` as a dependency.

### Usage

To generate a JSON schema for any Java class, simply call:

```java
import com.freakynit.openai.function.schema.generator.JsonSchemaGenerator;

public class Example {
    public static void main(String[] args) {
        String jsonSchema = JsonSchemaGenerator.generate(MyClass.class);
        System.out.println(jsonSchema);
    }
}
```

Where `MyClass` is any class you want to generate a schema for.

#### Example

Annotate your classes and fields with `@SchemaInfo` to customize the schema:

```java
public static void main(String[] args) {
    String schemaJson = new JsonSchemaGenerator().generate(SampleFunction.class);
    System.out.println(schemaJson);
}

@SchemaInfo(
        name = "sampleFunction",
        description = "This function does something interesting.",
        additionalProperties = false,  // class-level option
        strict = true                  // function-level option
)
class SampleFunction {
    @SchemaInfo(description = "A required string field.", required = true, format = "email")
    private String email;

    @SchemaInfo(description = "An optional number field.")
    private Integer count;

    @SchemaInfo(name="purchased_date", description = "A required date field.", required = true, format = "yyyy-mm-dd")
    private Date purchasedDate;

    @SchemaInfo(description = "Additional options for the function.")
    private Options options;
}

enum Status {IDLE, PROCESSING, DONE, FAILED};

class Options {
    @SchemaInfo(description = "A flag option.", required = true)
    private boolean flag;

    @SchemaInfo(description = "A list of items.", format = "uuid")
    private String[] items;

    @SchemaInfo(name="item_status", description = "Status of item.")
    private Status status;
}
```

#### Output
```json
{
  "type" : "function",
  "function" : {
    "name" : "sampleFunction",
    "description" : "This function does something interesting.",
    "strict" : true,
    "parameters" : {
      "type" : "object",
      "properties" : {
        "email" : {
          "type" : "string",
          "description" : "A required string field.",
          "format" : "email"
        },
        "count" : {
          "type" : "integer",
          "description" : "An optional number field."
        },
        "purchased_date" : {
          "type" : "datetime",
          "description" : "A required date field.",
          "format" : "yyyy-mm-dd"
        },
        "options" : {
          "type" : "object",
          "properties" : {
            "flag" : {
              "type" : "boolean",
              "description" : "A flag option."
            },
            "items" : {
              "type" : "array",
              "items" : {
                "type" : "string"
              },
              "description" : "A list of items.",
              "format" : "uuid"
            },
            "item_status" : {
              "type" : "string",
              "enum" : [ "IDLE", "PROCESSING", "DONE", "FAILED" ],
              "description" : "Status of item."
            }
          },
          "required" : [ "flag" ],
          "description" : "Additional options for the function."
        }
      },
      "required" : [ "email", "purchased_date" ],
      "additionalProperties" : false
    }
  }
}
```

See [VerdictTest.java](src/test/java/com/freakynit/verdict/VerdictTest.java) for comprehensive usage guide.

---

## Running Tests

A comprehensive suite of tests is provided using JUnit 5. To run the tests:

1. Ensure JUnit 5 is included in your project dependencies.
2. Compile the test files along with the source files.
3. Run the tests using your preferred build tool (e.g., Maven, Gradle) or directly from your IDE.

Tests are present in [VerdictTest.java](src/test/java/com/freakynit/verdict/VerdictTest.java).

The tests cover a wide range of scenarios including empty classes, primitives, collections, nested objects, enums, and annotation overrides.

---

## Contributing

Contributions, feedback, and feature requests are welcome! If you'd like to contribute:

- Fork the repository.
- Implement your changes.
- Submit a pull request or open an issue.

---

## License

This project is licensed under the MIT License. See the LICENSE file for details.
