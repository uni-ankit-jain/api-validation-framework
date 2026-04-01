package com.uniphore.platform.validation.filter;

import com.uniphore.platform.validation.annotation.HeaderConstraints;
import com.uniphore.platform.validation.annotation.SkipValidation;
import com.uniphore.platform.validation.annotation.ValidateHeader;
import com.uniphore.platform.validation.annotation.ValidateHeaders;
import com.uniphore.platform.validation.exception.HeaderValidationException;
import com.uniphore.platform.validation.properties.ValidationProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Core header validation filter. Fires at {@code HIGHEST_PRECEDENCE + 10}, after MDC setup
 * (CommonFilter) but before JWT decode (SecurityFilter).
 *
 * <p>Validation order:
 * <ol>
 *   <li>Bypass-path check — skip if matched</li>
 *   <li>{@code @SkipValidation} check — skip if present on handler method</li>
 *   <li>Authorization header — presence + Bearer prefix + non-empty token</li>
 *   <li>Content-Type — prefix match on POST/PUT/PATCH</li>
 *   <li>Global required custom headers (from properties)</li>
 *   <li>Per-endpoint {@code @ValidateHeader} annotations</li>
 * </ol>
 */
public class HeaderValidationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final List<String> MUTATING_METHODS = List.of("POST", "PUT", "PATCH");

    private final ValidationProperties properties;
    private final RequestMappingHandlerMapping handlerMapping;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public HeaderValidationFilter(ValidationProperties properties,
                                  RequestMappingHandlerMapping handlerMapping) {
        this.properties = properties;
        this.handlerMapping = handlerMapping;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        // Step 1 — bypass-path check
        if (isBypassPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Step 2 — resolve handler method and check @SkipValidation
        HandlerMethod handlerMethod = resolveHandlerMethod(request);
        if (handlerMethod != null && handlerMethod.hasMethodAnnotation(SkipValidation.class)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Resolve @HeaderConstraints once — method-level wins over class-level
        HeaderConstraints headerConstraints = resolveHeaderConstraints(handlerMethod);

        // Step 3 — validate Authorization header (skipped if @HeaderConstraints(skipAuth=true))
        boolean skipAuth = headerConstraints != null && headerConstraints.skipAuth();
        if (!skipAuth && properties.getAuthorizationHeader().isRequired()) {
            validateAuthorizationHeader(request);
        }

        // Step 4 — validate Content-Type on mutating requests
        // @HeaderConstraints.allowedContentTypes overrides the global list when non-empty
        if (properties.getContentType().isValidateOnMutating()
                && MUTATING_METHODS.contains(request.getMethod().toUpperCase())) {
            List<String> effectiveContentTypes =
                    (headerConstraints != null && headerConstraints.allowedContentTypes().length > 0)
                            ? Arrays.asList(headerConstraints.allowedContentTypes())
                            : properties.getContentType().getAllowedTypes();
            validateContentType(request, effectiveContentTypes);
        }

        // Step 5 — validate globally required custom headers (from properties)
        for (String headerName : properties.getCustomHeaders().getRequired()) {
            String value = request.getHeader(headerName);
            if (value == null || value.isBlank()) {
                throw new HeaderValidationException(
                        "Required header '" + headerName + "' is missing or blank",
                        HttpStatus.BAD_REQUEST,
                        headerName);
            }
        }

        // Step 5b — validate not-blank-if-present custom headers (from properties)
        for (String headerName : properties.getCustomHeaders().getNotBlankIfPresent()) {
            String value = request.getHeader(headerName);
            if (value != null && value.isBlank()) {
                throw new HeaderValidationException(
                        "Header '" + headerName + "' must not be blank when present",
                        HttpStatus.BAD_REQUEST,
                        headerName);
            }
        }

        // Step 5c — @HeaderConstraints required and notBlankIfPresent headers
        if (headerConstraints != null) {
            for (String headerName : headerConstraints.required()) {
                String value = request.getHeader(headerName);
                if (value == null || value.isBlank()) {
                    throw new HeaderValidationException(
                            "Required header '" + headerName + "' is missing or blank",
                            HttpStatus.BAD_REQUEST,
                            headerName);
                }
            }
            for (String headerName : headerConstraints.notBlankIfPresent()) {
                String value = request.getHeader(headerName);
                if (value != null && value.isBlank()) {
                    throw new HeaderValidationException(
                            "Header '" + headerName + "' must not be blank when present",
                            HttpStatus.BAD_REQUEST,
                            headerName);
                }
            }
        }

        // Step 6 — per-endpoint @ValidateHeader annotations (backward compatible)
        if (handlerMethod != null) {
            validatePerEndpointHeaders(request, handlerMethod);
        }

        filterChain.doFilter(request, response);
    }

    private boolean isBypassPath(String path) {
        return properties.getBypassPaths().stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private HandlerMethod resolveHandlerMethod(HttpServletRequest request) {
        try {
            Object handler = handlerMapping.getHandler(request);
            if (handler != null) {
                Object handlerObject = handler instanceof org.springframework.web.servlet.HandlerExecutionChain chain
                        ? chain.getHandler()
                        : handler;
                if (handlerObject instanceof HandlerMethod handlerMethod) {
                    return handlerMethod;
                }
            }
        } catch (Exception ignored) {
            // Handler not found — let the request proceed; DispatcherServlet will handle 404
        }
        return null;
    }

    private void validateAuthorizationHeader(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || authHeader.isBlank()) {
            throw new HeaderValidationException(
                    "Authorization header is required",
                    HttpStatus.UNAUTHORIZED,
                    "Authorization");
        }
        if (properties.getAuthorizationHeader().isRequireBearerPrefix()) {
            if (!authHeader.startsWith(BEARER_PREFIX)) {
                throw new HeaderValidationException(
                        "Authorization header must use Bearer scheme",
                        HttpStatus.UNAUTHORIZED,
                        "Authorization");
            }
            String token = authHeader.substring(BEARER_PREFIX.length()).strip();
            if (token.isEmpty()) {
                throw new HeaderValidationException(
                        "Authorization Bearer token must not be empty",
                        HttpStatus.UNAUTHORIZED,
                        "Authorization");
            }
        }
    }

    private void validateContentType(HttpServletRequest request, List<String> allowed) {
        String contentType = request.getContentType();
        if (contentType == null || contentType.isBlank()) {
            throw new HeaderValidationException(
                    "Content-Type header is required for " + request.getMethod() + " requests",
                    HttpStatus.BAD_REQUEST,
                    "Content-Type");
        }
        // Prefix match — allows "application/json; charset=UTF-8"
        boolean matched = allowed.stream()
                .anyMatch(type -> contentType.toLowerCase().startsWith(type.toLowerCase()));
        if (!matched) {
            throw new HeaderValidationException(
                    "Content-Type '" + contentType + "' is not allowed. Allowed types: " + allowed,
                    HttpStatus.BAD_REQUEST,
                    "Content-Type");
        }
    }

    /**
     * Returns the effective {@link HeaderConstraints} for the given handler method.
     * Method-level annotation takes full precedence over class-level.
     */
    private HeaderConstraints resolveHeaderConstraints(HandlerMethod handlerMethod) {
        if (handlerMethod == null) {
            return null;
        }
        HeaderConstraints methodLevel = handlerMethod.getMethodAnnotation(HeaderConstraints.class);
        if (methodLevel != null) {
            return methodLevel;
        }
        return handlerMethod.getBeanType().getAnnotation(HeaderConstraints.class);
    }

    private void validatePerEndpointHeaders(HttpServletRequest request, HandlerMethod handlerMethod) {
        // Collect annotations: class-level first, then method-level (method overrides class)
        ValidateHeader[] classAnnotations = getValidateHeaders(handlerMethod.getBeanType());
        ValidateHeader[] methodAnnotations = getValidateHeaders(handlerMethod.getMethod());

        // Build effective map: method-level wins over class-level for same header name
        Map<String, ValidateHeader> effective = new java.util.LinkedHashMap<>();
        for (ValidateHeader ann : classAnnotations) {
            effective.put(ann.name(), ann);
        }
        for (ValidateHeader ann : methodAnnotations) {
            effective.put(ann.name(), ann);  // override class-level
        }

        for (ValidateHeader ann : effective.values()) {
            String value = request.getHeader(ann.name());
            if (ann.required() && (value == null || value.isBlank())) {
                String msg = ann.message().isBlank()
                        ? "Required header '" + ann.name() + "' is missing or blank"
                        : ann.message();
                throw new HeaderValidationException(msg, HttpStatus.UNPROCESSABLE_ENTITY, ann.name());
            }
            if (value != null && ann.notBlank() && value.isBlank()) {
                String msg = ann.message().isBlank()
                        ? "Header '" + ann.name() + "' must not be blank"
                        : ann.message();
                throw new HeaderValidationException(msg, HttpStatus.UNPROCESSABLE_ENTITY, ann.name());
            }
        }
    }

    private ValidateHeader[] getValidateHeaders(java.lang.reflect.AnnotatedElement element) {
        ValidateHeaders container = element.getAnnotation(ValidateHeaders.class);
        if (container != null) {
            return container.value();
        }
        ValidateHeader single = element.getAnnotation(ValidateHeader.class);
        return single != null ? new ValidateHeader[]{single} : new ValidateHeader[0];
    }
}
