package com.uniphore.platform.validation.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a validation rule for a single HTTP header.
 * Used only as a member of {@link HeaderConstraints}.
 *
 * <p>Examples:
 * <pre>{@code
 * @HeaderConstraints({
 *     @HeaderRule(name = "X-Tenant-ID"),                            // required, must be non-blank
 *     @HeaderRule(name = "X-Source", required = false),             // notBlankIfPresent
 *     @HeaderRule(name = "X-Debug",  required = false, notBlank = false), // presence not required, blank allowed
 *     @HeaderRule(name = "X-Custom", message = "Provide X-Custom")  // custom error message
 * })
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({})  // only valid as a member inside @HeaderConstraints
public @interface HeaderRule {

    /** The HTTP header name to validate (e.g. {@code "X-Tenant-ID"}). */
    String name();

    /**
     * Whether the header must be present.
     * When {@code false}, the header is optional but subject to {@link #notBlank()} if present.
     * Defaults to {@code true}.
     */
    boolean required() default true;

    /**
     * When the header is present, whether its value must be non-blank.
     * Defaults to {@code true}.
     */
    boolean notBlank() default true;

    /**
     * Custom validation failure message.
     * When blank, a default message is generated from the header name and rule.
     */
    String message() default "";
}
