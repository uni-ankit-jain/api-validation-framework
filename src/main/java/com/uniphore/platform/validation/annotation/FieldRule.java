package com.uniphore.platform.validation.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares validation rules for a single request body field.
 * Used only as a member of {@link FieldConstraints}.
 *
 * <p>Each rule can enforce allowed values, a length constraint, or both.
 * Omit any attribute that is not needed — defaults mean "no constraint".
 *
 * <p>Examples:
 * <pre>{@code
 * @FieldConstraints({
 *     // allowed-values only
 *     @FieldRule(field = "status",      values = {"ACTIVE", "INACTIVE"}),
 *
 *     // length only
 *     @FieldRule(field = "name",        min = 2, max = 100),
 *
 *     // both
 *     @FieldRule(field = "countryCode", values = {"US", "IN", "GB"}, min = 2, max = 2),
 *
 *     // case-insensitive allowed-values
 *     @FieldRule(field = "priority",    values = {"low", "medium", "high"}, ignoreCase = true)
 * })
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({})  // only valid as a member inside @FieldConstraints
public @interface FieldRule {

    /** Name of the field in the request body object. */
    String field();

    /**
     * Exhaustive list of allowed values for the field.
     * When empty (the default), no value constraint is applied.
     */
    String[] values() default {};

    /**
     * When {@code true}, allowed-values comparison is case-insensitive.
     * Defaults to {@code false}.
     */
    boolean ignoreCase() default false;

    /**
     * Minimum field length (inclusive).
     * For {@code String} fields — character count; for {@code Collection} fields — element count.
     * {@code -1} (the default) means no minimum is enforced.
     */
    int min() default -1;

    /**
     * Maximum field length (inclusive).
     * For {@code String} fields — character count; for {@code Collection} fields — element count.
     * {@code -1} (the default) means no maximum is enforced.
     */
    int max() default -1;

    /**
     * Custom error message. When blank, a default message is generated.
     * Applies to both value and length violations on this field.
     */
    String message() default "";
}
