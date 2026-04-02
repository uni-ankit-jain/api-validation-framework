package com.uniphore.platform.validation.aspect;

import com.uniphore.platform.validation.annotation.FieldConstraints;
import com.uniphore.platform.validation.annotation.FieldLengthConstraints;
import com.uniphore.platform.validation.annotation.FieldRule;
import com.uniphore.platform.validation.annotation.LengthRule;
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
 * AOP aspect that enforces {@link FieldConstraints} and {@link FieldLengthConstraints} rules
 * declared on controller methods. Both annotation types are evaluated in a single intercept so
 * a method carrying both annotations executes exactly once.
 *
 * <p>Execution order:
 * <ol>
 *   <li>Locate the {@code @RequestBody} argument in the join point.</li>
 *   <li>Validate allowed-value rules from {@link FieldConstraints} (if present).</li>
 *   <li>Validate length rules from {@link FieldLengthConstraints} (if present).</li>
 *   <li>Null field values are always skipped — pair with {@code @NotNull} on the DTO if needed.</li>
 *   <li>All violations are collected before throwing, so the response reports every invalid field at once.</li>
 * </ol>
 */
@Aspect
public class FieldConstraintsAspect {

    private static final Logger log = LoggerFactory.getLogger(FieldConstraintsAspect.class);

    /**
     * Intercepts methods where either {@link FieldConstraints} or {@link FieldLengthConstraints}
     * is present (on the method or the declaring class) and validates the {@code @RequestBody}
     * argument against the declared rules.
     */
    @Around("(@annotation(com.uniphore.platform.validation.annotation.FieldConstraints) " +
            "|| @within(com.uniphore.platform.validation.annotation.FieldConstraints) " +
            "|| @annotation(com.uniphore.platform.validation.annotation.FieldLengthConstraints) " +
            "|| @within(com.uniphore.platform.validation.annotation.FieldLengthConstraints)) " +
            "&& execution(* *(..))")
    public Object validate(ProceedingJoinPoint pjp) throws Throwable {
        Object requestBody = findRequestBody(pjp);
        if (requestBody == null) {
            return pjp.proceed();
        }

        Map<String, List<String>> errors = new LinkedHashMap<>();

        FieldConstraints fieldConstraints = resolveFieldConstraints(pjp);
        if (fieldConstraints != null) {
            validateAllowedValues(requestBody, fieldConstraints, errors);
        }

        FieldLengthConstraints lengthConstraints = resolveFieldLengthConstraints(pjp);
        if (lengthConstraints != null) {
            validateLengths(requestBody, lengthConstraints, errors);
        }

        if (!errors.isEmpty()) {
            throw new BodyValidationException("Field validation failed", errors);
        }

        return pjp.proceed();
    }

    // -------------------------------------------------------------------------
    // Validation logic
    // -------------------------------------------------------------------------

    private void validateAllowedValues(Object requestBody, FieldConstraints constraints,
                                       Map<String, List<String>> errors) {
        for (FieldRule rule : constraints.value()) {
            Object value = readField(requestBody, rule.field());
            if (value == null) {
                continue;
            }
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
    }

    private void validateLengths(Object requestBody, FieldLengthConstraints constraints,
                                  Map<String, List<String>> errors) {
        for (LengthRule rule : constraints.value()) {
            Object value = readField(requestBody, rule.field());
            if (value == null) {
                continue;
            }

            int actual = measureLength(value, rule.field());
            if (actual < 0) {
                continue;  // unsupported type — already warned in measureLength
            }

            if (actual < rule.min() || actual > rule.max()) {
                String msg = rule.message().isEmpty()
                        ? buildLengthMessage(rule.field(), rule.min(), rule.max(), actual)
                        : rule.message();
                errors.computeIfAbsent(rule.field(), k -> new ArrayList<>()).add(msg);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Method-level annotation wins over class-level. */
    private FieldConstraints resolveFieldConstraints(ProceedingJoinPoint pjp) {
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        FieldConstraints methodLevel = method.getAnnotation(FieldConstraints.class);
        if (methodLevel != null) {
            return methodLevel;
        }
        return pjp.getTarget().getClass().getAnnotation(FieldConstraints.class);
    }

    /** Method-level annotation wins over class-level. */
    private FieldLengthConstraints resolveFieldLengthConstraints(ProceedingJoinPoint pjp) {
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        FieldLengthConstraints methodLevel = method.getAnnotation(FieldLengthConstraints.class);
        if (methodLevel != null) {
            return methodLevel;
        }
        return pjp.getTarget().getClass().getAnnotation(FieldLengthConstraints.class);
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
     * <ul>
     *   <li>{@code String} → character count</li>
     *   <li>{@code Collection} → element count</li>
     * </ul>
     * Returns {@code -1} for unsupported types (warning logged).
     */
    private int measureLength(Object value, String fieldName) {
        if (value instanceof String s) {
            return s.length();
        }
        if (value instanceof Collection<?> c) {
            return c.size();
        }
        log.warn("@LengthRule on field '{}' — unsupported type {}. Rule will be skipped.",
                fieldName, value.getClass().getSimpleName());
        return -1;
    }

    private String buildLengthMessage(String field, int min, int max, int actual) {
        if (min > 0 && max < Integer.MAX_VALUE) {
            return "Field '" + field + "' length must be between " + min + " and " + max
                    + " (actual: " + actual + ")";
        }
        if (max < Integer.MAX_VALUE) {
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
