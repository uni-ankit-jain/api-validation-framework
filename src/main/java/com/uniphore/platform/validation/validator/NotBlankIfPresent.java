package com.uniphore.platform.validation.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * JSR-303 constraint for optional-but-non-blank string fields.
 * Passes validation when the value is {@code null} (field is absent),
 * but fails when the value is an empty or whitespace-only string.
 *
 * <p>Example:
 * <pre>{@code
 * @NotBlankIfPresent
 * private String optionalNotes;  // null → OK, "" or "  " → fails
 * }</pre>
 */
@Documented
@Constraint(validatedBy = NotBlankIfPresentValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface NotBlankIfPresent {

    String message() default "Field must not be blank when provided";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
