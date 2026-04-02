package com.uniphore.platform.validation.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a length constraint for a single request body field.
 * Used only as a member of {@link FieldLengthConstraints}.
 *
 * <p>For {@code String} fields the constraint applies to the character count.
 * For {@code Collection} fields it applies to the element count.
 * Null values are skipped — pair with {@code @NotNull} on the DTO to reject null.
 *
 * <p>Examples:
 * <pre>{@code
 * @FieldLengthConstraints({
 *     @LengthRule(field = "name",        min = 2,  max = 100),
 *     @LengthRule(field = "description", max = 500),
 *     @LengthRule(field = "tags",        max = 10,  message = "No more than 10 tags allowed")
 * })
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({})  // only valid as a member inside @FieldLengthConstraints
public @interface LengthRule {

    /** Name of the field in the request body object. */
    String field();

    /**
     * Minimum length (inclusive).
     * Defaults to {@code 0} (no minimum).
     */
    int min() default 0;

    /**
     * Maximum length (inclusive).
     * Defaults to {@code Integer.MAX_VALUE} (no maximum).
     */
    int max() default Integer.MAX_VALUE;

    /**
     * Custom error message. When blank, a default message is generated:
     * "Field '{field}' length must be between {min} and {max} (actual: {actual})"
     */
    String message() default "";
}
