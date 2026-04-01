package com.uniphore.platform.validation.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares field-level allowed-value rules for a controller method or class.
 * Rules are enforced by {@code FieldConstraintsAspect} against the first
 * {@code @RequestBody} parameter of the annotated method.
 *
 * <p>Null field values are always considered valid — combine with {@code @NotNull}
 * on the DTO field if null should be rejected.
 *
 * <p>Example:
 * <pre>{@code
 * @FieldConstraints({
 *     @FieldRule(field = "status",   values = {"ACTIVE", "INACTIVE", "PENDING"}),
 *     @FieldRule(field = "priority", values = {"LOW", "MEDIUM", "HIGH"}, ignoreCase = true)
 * })
 * @PostMapping("/orders")
 * public ResponseEntity<OrderResponse> createOrder(@RequestBody @Valid OrderRequest req) { ... }
 * }</pre>
 *
 * <p>When placed on a class, the rules apply to every handler method within it.
 * Method-level annotations take precedence over class-level ones when both are present.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface FieldConstraints {

    FieldRule[] value();
}
