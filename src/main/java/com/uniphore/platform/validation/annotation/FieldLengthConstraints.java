package com.uniphore.platform.validation.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares field length constraints for a controller method or class using inline
 * {@link LengthRule} entries — the same pattern as {@link FieldConstraints} / {@link FieldRule}.
 *
 * <p>Validated against the first {@code @RequestBody} parameter of the annotated method.
 * When placed on a class, applies to every handler method within it.
 * A method-level annotation takes full precedence over a class-level one.
 *
 * <p>Can be combined with {@link FieldConstraints} on the same method — both are evaluated
 * together before the method executes, and all violations are reported at once.
 *
 * <p>Example:
 * <pre>{@code
 * @FieldLengthConstraints({
 *     @LengthRule(field = "name",        min = 2,  max = 100),
 *     @LengthRule(field = "description", max = 500),
 *     @LengthRule(field = "tags",        max = 10, message = "No more than 10 tags allowed")
 * })
 * @PostMapping("/products")
 * public ResponseEntity<Product> create(@RequestBody @Valid ProductRequest req) { ... }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface FieldLengthConstraints {

    /** Inline per-field length rules. */
    LengthRule[] value() default {};
}
