package com.uniphore.platform.validation.handler;

import com.uniphore.platform.validation.exception.BodyValidationException;
import com.uniphore.platform.validation.exception.HeaderValidationException;
import com.uniphore.platform.validation.model.ValidationError;
import com.uniphore.platform.validation.model.ValidationErrorResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global exception handler for validation errors.
 * Conditionally registered via {@code uniphore.validation.exception-handler.enabled=true}.
 * Services with an existing {@code AppExceptionHandler} should set that property to {@code false}
 * and handle {@link HeaderValidationException} directly in their own advice.
 */
@RestControllerAdvice
public class ValidationExceptionHandler {

    private static final String MDC_TRACE_ID = "auditTraceId";
    private static final String MDC_TENANT_ID = "auditTenantId";
    private static final String REQUEST_ATTR_TRACE_ID = "traceId";
    private static final String REQUEST_ATTR_TENANT_ID = "tenantId";

    @ExceptionHandler(HeaderValidationException.class)
    public ResponseEntity<ValidationErrorResponse> handleHeaderValidation(HeaderValidationException ex) {
        HttpStatus status = ex.getHttpStatus();
        ValidationErrorResponse body = ValidationErrorResponse.builder()
                .timestamp(Instant.now().toString())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(ex.getMessage())
                .traceId(resolveTraceId())
                .tenantId(resolveTenantId())
                .errors(List.of(new ValidationError(ex.getHeaderName(), ex.getMessage())))
                .build();
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(BodyValidationException.class)
    public ResponseEntity<ValidationErrorResponse> handleBodyValidation(BodyValidationException ex) {
        List<ValidationError> errors = ex.getFieldErrors().entrySet().stream()
                .flatMap(entry -> entry.getValue().stream()
                        .map(msg -> new ValidationError(entry.getKey(), msg)))
                .collect(Collectors.toList());

        ValidationErrorResponse body = ValidationErrorResponse.builder()
                .timestamp(Instant.now().toString())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message(ex.getMessage())
                .traceId(resolveTraceId())
                .tenantId(resolveTenantId())
                .errors(errors)
                .build();
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex) {

        List<ValidationError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ValidationError(
                        fe.getField(),
                        fe.getDefaultMessage(),
                        fe.getRejectedValue()))
                .collect(Collectors.toList());

        String message = errors.isEmpty() ? "Validation failed" : errors.get(0).getMessage();

        ValidationErrorResponse body = ValidationErrorResponse.builder()
                .timestamp(Instant.now().toString())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message(message)
                .traceId(resolveTraceId())
                .tenantId(resolveTenantId())
                .errors(errors)
                .build();
        return ResponseEntity.badRequest().body(body);
    }

    private String resolveTraceId() {
        String value = MDC.get(MDC_TRACE_ID);
        if (value != null) return value;
        return resolveFromRequestAttributes(REQUEST_ATTR_TRACE_ID);
    }

    private String resolveTenantId() {
        String value = MDC.get(MDC_TENANT_ID);
        if (value != null) return value;
        return resolveFromRequestAttributes(REQUEST_ATTR_TENANT_ID);
    }

    private String resolveFromRequestAttributes(String key) {
        try {
            RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                Object value = attrs.getAttribute(key, RequestAttributes.SCOPE_REQUEST);
                return value != null ? value.toString() : null;
            }
        } catch (Exception ignored) {
            // Not in a request context
        }
        return null;
    }
}
