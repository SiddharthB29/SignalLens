package com.signallens.ingestion.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = MetadataMaxDepthValidator.class)
@Target({
        ElementType.FIELD,
        ElementType.PARAMETER,
        ElementType.METHOD,
        ElementType.ANNOTATION_TYPE,
        ElementType.RECORD_COMPONENT
})
@Retention(RetentionPolicy.RUNTIME)
public @interface MetadataMaxDepth {

    String message() default "metadata must not exceed the maximum nesting depth";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    int value() default 5;
}