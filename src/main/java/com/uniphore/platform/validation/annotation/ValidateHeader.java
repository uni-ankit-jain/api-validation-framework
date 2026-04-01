package com.uniphore.platform.validation.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a required (or conditionally required) HTTP header for a controller method or class.
 * When placed on a class, applies to all handler methods within it unless overridden.
 * Repeatable — multiple headers can be declared on the same element.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@Repeatable(ValidateHeaders.class)
public @interface ValidateHeader {

    /** The header name to validate (e.g. "X-Tenant-ID"). */
    String name();

    /** Whether the header must be present. Defaults to true. */
    boolean required() default true;

    /** When present, the header value must not be blank. Defaults to true. */
    boolean notBlank() default true;

    /** Custom validation failure message. Defaults to a generated message. */
    String message() default "";
}
