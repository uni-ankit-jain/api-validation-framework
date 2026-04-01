package com.uniphore.platform.validation.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares an allowed-values rule for a single request body field.
 * Used only as a member of {@link FieldConstraints}.
 *
 * <p>Example:
 * <pre>{@code
 * @FieldConstraints({
 *     @FieldRule(field = "status", values = {"ACTIVE", "INACTIVE"}),
 *     @FieldRule(field = "priority", values = {"LOW", "MEDIUM", "HIGH"}, ignoreCase = true)
 * })
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({})  // only valid as a member inside @FieldConstraints
public @interface FieldRule {

    /** Name of the field in the request body object. */
    String field();

    /** Exhaustive list of allowed values for the field. */
    String[] values();

    /**
     * When {@code true}, comparison is case-insensitive.
     * Defaults to {@code false}.
     */
    boolean ignoreCase() default false;

    /**
     * Custom error message. If left empty, a default message is generated:
     * "Value '&lt;actual&gt;' is not allowed. Allowed: [values]"
     */
    String message() default "";
}
