package com.uniphore.platform.validation.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Container annotation for repeatable {@link ValidateHeader}.
 * Not typically used directly — the compiler generates this when multiple
 * {@code @ValidateHeader} annotations appear on the same element.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface ValidateHeaders {

    ValidateHeader[] value();
}
