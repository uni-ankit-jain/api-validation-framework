package com.uniphore.platform.validation.aspect;

import com.uniphore.platform.validation.annotation.FieldConstraints;
import com.uniphore.platform.validation.annotation.FieldRule;
import com.uniphore.platform.validation.exception.BodyValidationException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestBody;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AOP aspect that enforces {@link FieldConstraints} rules declared on controller methods.
 *
 * <p>Each {@link FieldRule} may declare allowed values, a length constraint, or both.
 * All violations across all rules are collected before throwing, so the response
 * reports every invalid field at once.
 *
 * <p>Null field values are always skipped — pair with {@code @NotNull} on the DTO if needed.
 */
@Aspect
public class FieldConstraintsAspect {

    private static final Logger log = LoggerFactory.getLogger(FieldConstraintsAspect.class);

    @Around("(@annotation(com.uniphore.platform.validation.annotation.FieldConstraints) " +
            "|| @within(com.uniphore.platform.validation.annotation.FieldConstraints)) " +
            "&& execution(* *(..))")
    public Object validate(ProceedingJoinPoint pjp) throws Throwable {
        FieldConstraints constraints = resolveAnnotation(pjp);
        if (constraints == null) {
            return pjp.proceed();
        }

        Object requestBody = findRequestBody(pjp);
        if (requestBody == null) {
            return pjp.proceed();
        }

        Map<String, List<String>> errors = new LinkedHashMap<>();

        for (FieldRule rule : constraints.value()) {
            Object value = readField(requestBody, rule.field());
            if (value == null) {
                continue;  // null is valid — use @NotNull on the DTO field to reject it
            }

            // Allowed-values check
            if (rule.values().length > 0) {
                String actual = value.toString();
                Set<String> allowed = buildAllowedSet(rule);
                String toCheck = rule.ignoreCase() ? actual.toLowerCase() : actual;
                if (!allowed.contains(toCheck)) {
                    String msg = rule.message().isEmpty()
                            ? "Value '" + actual + "' is not allowed. Allowed values: " + Arrays.toString(rule.values())
                            : rule.message();
                    errors.computeIfAbsent(rule.field(), k -> new ArrayList<>()).add(msg);
                }
            }

            // Length check
            if (rule.min() >= 0 || rule.max() >= 0) {
                int length = measureLength(value, rule.field());
                if (length >= 0) {
                    int effectiveMin = rule.min() >= 0 ? rule.min() : 0;
                    int effectiveMax = rule.max() >= 0 ? rule.max() : Integer.MAX_VALUE;
                    if (length < effectiveMin || length > effectiveMax) {
                        String msg = rule.message().isEmpty()
                                ? buildLengthMessage(rule.field(), rule.min(), rule.max(), length)
                                : rule.message();
                        errors.computeIfAbsent(rule.field(), k -> new ArrayList<>()).add(msg);
                    }
                }
            }
        }

        if (!errors.isEmpty()) {
            throw new BodyValidationException("Field validation failed", errors);
        }

        return pjp.proceed();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Method-level annotation wins over class-level. */
    private FieldConstraints resolveAnnotation(ProceedingJoinPoint pjp) {
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        FieldConstraints methodLevel = method.getAnnotation(FieldConstraints.class);
        if (methodLevel != null) {
            return methodLevel;
        }
        return pjp.getTarget().getClass().getAnnotation(FieldConstraints.class);
    }

    /** Returns the first argument annotated with {@code @RequestBody}, or {@code null}. */
    private Object findRequestBody(ProceedingJoinPoint pjp) {
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        Annotation[][] paramAnnotations = method.getParameterAnnotations();
        Object[] args = pjp.getArgs();

        for (int i = 0; i < paramAnnotations.length; i++) {
            for (Annotation ann : paramAnnotations[i]) {
                if (ann instanceof RequestBody) {
                    return args[i];
                }
            }
        }
        return null;
    }

    /**
     * Reads a declared field value via reflection, walking the class hierarchy.
     * Returns {@code null} if the field is not found or not accessible.
     */
    private Object readField(Object target, String fieldName) {
        Class<?> clazz = target.getClass();
        while (clazz != null && clazz != Object.class) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            } catch (IllegalAccessException e) {
                log.warn("Cannot access field '{}' on {}: {}",
                        fieldName, target.getClass().getSimpleName(), e.getMessage());
                return null;
            }
        }
        log.warn("Field '{}' not found on {}. Rule will be skipped.",
                fieldName, target.getClass().getSimpleName());
        return null;
    }

    /**
     * Returns the measurable length of a value:
     * {@code String} → character count, {@code Collection} → element count.
     * Returns {@code -1} for unsupported types (warning logged).
     */
    private int measureLength(Object value, String fieldName) {
        if (value instanceof String s) {
            return s.length();
        }
        if (value instanceof Collection<?> c) {
            return c.size();
        }
        log.warn("@FieldRule min/max on field '{}' — unsupported type {}. Length check skipped.",
                fieldName, value.getClass().getSimpleName());
        return -1;
    }

    private String buildLengthMessage(String field, int min, int max, int actual) {
        if (min >= 0 && max >= 0) {
            return "Field '" + field + "' length must be between " + min + " and " + max
                    + " (actual: " + actual + ")";
        }
        if (max >= 0) {
            return "Field '" + field + "' must not exceed " + max
                    + " characters (actual: " + actual + ")";
        }
        return "Field '" + field + "' must be at least " + min
                + " characters (actual: " + actual + ")";
    }

    private Set<String> buildAllowedSet(FieldRule rule) {
        return Arrays.stream(rule.values())
                .map(rule.ignoreCase() ? String::toLowerCase : v -> v)
                .collect(Collectors.toSet());
    }
}
