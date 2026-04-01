package com.uniphore.platform.validation.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * JSR-303 constraint that validates a string field against a whitelist regex pattern.
 * Use this to prevent injection attacks by allowing only known-safe characters.
 *
 * <p>Example:
 * <pre>{@code
 * @SafeString(pattern = "^[a-zA-Z0-9 _-]+$")
 * private String name;
 * }</pre>
 */
@Documented
@Constraint(validatedBy = SafeStringValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface SafeString {

    /**
     * Whitelist regex pattern. The entire field value must match.
     * Defaults to alphanumeric, spaces, hyphens, and underscores.
     */
    String pattern() default "^[\\w\\s\\-]+$";

    String message() default "Field contains invalid characters";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
