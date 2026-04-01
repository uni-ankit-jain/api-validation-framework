package com.uniphore.platform.validation.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Opts a controller method out of all header validation performed by
 * {@code HeaderValidationFilter}. Useful for public endpoints (e.g. webhooks,
 * health checks not covered by bypass-paths).
 * Method-level only; cannot be placed on a class.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SkipValidation {
}
