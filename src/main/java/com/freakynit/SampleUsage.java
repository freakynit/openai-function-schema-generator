package com.freakynit;

import com.freakynit.openai.function.schema.generator.JsonSchemaGenerator;
import com.freakynit.openai.function.schema.generator.SchemaInfo;

import java.util.Date;

public class SampleUsage {
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
}