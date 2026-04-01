package com.uniphore.platform.validation.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Consolidates all header validation rules for a controller method or class into a single
 * annotation, replacing the need to stack multiple {@link ValidateHeader} annotations and
 * scatter related config across {@code application.properties}.
 *
 * <p>When placed on a class, applies to every handler method within it.
 * A method-level annotation takes full precedence over a class-level one.
 *
 * <p>Example:
 * <pre>{@code
 * @HeaderConstraints(
 *     required            = {"X-Tenant-ID", "X-Correlation-ID"},
 *     notBlankIfPresent   = {"X-Source"},
 *     skipAuth            = false,
 *     allowedContentTypes = {"application/json", "application/xml"}
 * )
 * @PostMapping("/orders")
 * public ResponseEntity<OrderResponse> createOrder(@RequestBody @Valid OrderRequest req) { ... }
 * }</pre>
 *
 * <p>Interaction with global properties:
 * <ul>
 *   <li>{@link #skipAuth()} overrides {@code uniphore.validation.authorization-header.required}
 *       for this endpoint only.</li>
 *   <li>{@link #allowedContentTypes()} overrides {@code uniphore.validation.content-type.allowed-types}
 *       for this endpoint when non-empty; falls through to the global list when empty.</li>
 *   <li>Global {@code custom-headers.required} and {@code custom-headers.not-blank-if-present}
 *       from properties still apply alongside the annotation rules.</li>
 * </ul>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface HeaderConstraints {

    /**
     * Header names that must be present and non-blank on the request.
     * Failures return {@code 400 Bad Request}.
     */
    String[] required() default {};

    /**
     * Header names that, when present, must not be blank.
     * Null / absent values are allowed; empty or whitespace values are rejected.
     * Failures return {@code 400 Bad Request}.
     */
    String[] notBlankIfPresent() default {};

    /**
     * When {@code true}, skips the Authorization header check for this endpoint,
     * even if {@code uniphore.validation.authorization-header.required=true} globally.
     * Useful for public or webhook endpoints that use a different auth mechanism.
     */
    boolean skipAuth() default false;

    /**
     * Overrides the global {@code uniphore.validation.content-type.allowed-types} list
     * for this endpoint. Prefix-matched (e.g. {@code "application/json"} matches
     * {@code "application/json; charset=UTF-8"}).
     * When empty (the default), the global allowed-types list is used.
     */
    String[] allowedContentTypes() default {};
}
