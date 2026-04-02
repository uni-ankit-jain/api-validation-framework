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
 *
 * <h3>Protobuf support</h3>
 * <p>When {@code protobuf-java} is on the runtime classpath, request bodies that implement
 * {@code com.google.protobuf.Message} are validated via the protobuf Descriptors API instead
 * of reflection.  This means:
 * <ul>
 *   <li>Field names must match the proto field name (snake_case as defined in the {@code .proto}
 *       file).</li>
 *   <li>Dot-notation paths resolve nested messages (e.g. {@code "address.city"}).</li>
 *   <li>Enum values are compared by their name string (e.g. {@code "ACTIVE"}).</li>
 *   <li>Repeated fields support min/max element-count constraints.</li>
 *   <li>Optional/message fields not yet set are treated as {@code null} (rule skipped).</li>
 * </ul>
 * <p>Protobuf-java is declared as an optional dependency; if absent the aspect falls back to
 * the standard reflection path and works identically for regular POJO request bodies.
 */
@Aspect
public class FieldConstraintsAspect {

    private static final Logger log = LoggerFactory.getLogger(FieldConstraintsAspect.class);

    /**
     * {@code true} when {@code com.google.protobuf.Message} is resolvable at runtime.
     * Guards all references to {@link ProtoFieldReader} so the class is never loaded
     * (and does not cause {@link NoClassDefFoundError}) when protobuf-java is absent.
     */
    private static final boolean PROTOBUF_PRESENT;

    static {
        boolean present = false;
        try {
            Class.forName("com.google.protobuf.Message");
            present = true;
        } catch (ClassNotFoundException ignored) {
        }
        PROTOBUF_PRESENT = present;
    }

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
     * Reads a field value from a request-body object.
     *
     * <p>When {@code protobuf-java} is on the classpath and {@code target} is a
     * {@code com.google.protobuf.Message}, delegates to {@link ProtoFieldReader} which uses
     * the Descriptors API and supports dot-notation paths for nested messages.
     * Otherwise falls back to reflection over the Java class hierarchy.
     *
     * @param target    the request-body object
     * @param fieldName field name (or dot-separated path for protobuf nested messages)
     * @return field value, or {@code null} if absent / not accessible
     */
    private Object readField(Object target, String fieldName) {
        if (PROTOBUF_PRESENT && ProtoFieldReader.isProtoMessage(target)) {
            return ProtoFieldReader.readField(target, fieldName, log);
        }

        // POJO path: walk the class hierarchy via reflection
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
     *   <li>{@code Collection} → element count (covers protobuf repeated fields, returned as
     *       {@code List<?>} by the Descriptors API)</li>
     *   <li>{@code com.google.protobuf.ByteString} → byte count (when protobuf is present)</li>
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
        if (PROTOBUF_PRESENT && value instanceof com.google.protobuf.ByteString bs) {
            return bs.size();
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
