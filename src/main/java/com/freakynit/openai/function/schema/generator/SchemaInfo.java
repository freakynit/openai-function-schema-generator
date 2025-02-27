package com.freakynit.openai.function.schema.generator;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Custom annotation to add metadata.
 * <p>
 * Note:
 * <ul>
 *   <li>`name`, `description`, and `required` can be used on classes, fields, or methods.</li>
 *   <li>`format` is used for field-level annotations only.</li>
 *   <li>`additionalProperties` is used at the class level and will be added to the parameters object.</li>
 *   <li>`strict` is used at the function level (class-level) and will be added to the function object.</li>
 * </ul>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD})
public @interface SchemaInfo {
    String name() default "";
    String description() default "";
    boolean required() default false;

    // Field-level only: to specify additional string format info.
    String format() default "";

    // Class-level only: to specify if additionalProperties should be allowed.
    boolean additionalProperties() default false;

    // Function-level only: to indicate whether the function should be strict.
    boolean strict() default false;
}
