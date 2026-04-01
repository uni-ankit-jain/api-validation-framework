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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AOP aspect that enforces {@link FieldConstraints} rules declared on controller methods.
 *
 * <p>Execution order:
 * <ol>
 *   <li>Locate the {@code @RequestBody} argument in the join point.</li>
 *   <li>For each {@link FieldRule}, read the field value via reflection.</li>
 *   <li>Null values are skipped (pair with {@code @NotNull} on the DTO if needed).</li>
 *   <li>All violations are collected before throwing, so the response reports every invalid field at once.</li>
 * </ol>
 */
@Aspect
public class FieldConstraintsAspect {

    private static final Logger log = LoggerFactory.getLogger(FieldConstraintsAspect.class);

    /**
     * Intercepts methods annotated with {@link FieldConstraints} and validates
     * the {@code @RequestBody} argument against the declared rules.
     * Method-level annotation takes precedence over class-level.
     */
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

        if (!errors.isEmpty()) {
            throw new BodyValidationException("Field value validation failed", errors);
        }

        return pjp.proceed();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Method-level annotation wins over class-level when both exist.
     */
    private FieldConstraints resolveAnnotation(ProceedingJoinPoint pjp) {
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        FieldConstraints methodLevel = method.getAnnotation(FieldConstraints.class);
        if (methodLevel != null) {
            return methodLevel;
        }
        return pjp.getTarget().getClass().getAnnotation(FieldConstraints.class);
    }

    /**
     * Returns the first argument annotated with {@code @RequestBody}, or {@code null}.
     */
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
     * Reads a declared field value from the target object via reflection.
     * Searches the declared class and its superclasses.
     * Returns {@code null} if the field doesn't exist or isn't accessible.
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
                log.warn("Cannot access field '{}' on {}: {}", fieldName, target.getClass().getSimpleName(), e.getMessage());
                return null;
            }
        }
        log.warn("@FieldRule references unknown field '{}' on {}. Rule will be skipped.",
                fieldName, target.getClass().getSimpleName());
        return null;
    }

    private Set<String> buildAllowedSet(FieldRule rule) {
        return Arrays.stream(rule.values())
                .map(rule.ignoreCase() ? String::toLowerCase : v -> v)
                .collect(Collectors.toSet());
    }
}
