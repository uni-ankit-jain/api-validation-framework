package com.uniphore.platform.validation.filter;

import com.uniphore.platform.validation.annotation.HeaderConstraints;
import com.uniphore.platform.validation.annotation.HeaderRule;
import com.uniphore.platform.validation.annotation.SkipValidation;
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
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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
 *   <li>Per-endpoint {@link HeaderConstraints} / {@link HeaderRule} annotations</li>
 * </ol>
 */
public class HeaderValidationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final List<String> MUTATING_METHODS = List.of("POST", "PUT", "PATCH");

    private final ValidationProperties properties;
    private final RequestMappingHandlerMapping handlerMapping;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    // Resolved @HeaderConstraints per method (method-level wins over class-level)
    private final ConcurrentHashMap<Method, Optional<HeaderConstraints>> headerConstraintsCache = new ConcurrentHashMap<>();

    // Cached lowercased effective content-type lists per method (for per-endpoint annotation overrides)
    private final ConcurrentHashMap<Method, List<String>> effectiveContentTypesCache = new ConcurrentHashMap<>();

    // Bypass-path match result per request URI — avoids repeated AntPathMatcher evaluation
    private final ConcurrentHashMap<String, Boolean> bypassPathCache = new ConcurrentHashMap<>();

    // Global allowed content types pre-lowercased at construction time
    private final List<String> globalAllowedContentTypesLower;

    public HeaderValidationFilter(ValidationProperties properties,
                                  RequestMappingHandlerMapping handlerMapping) {
        this.properties = properties;
        this.handlerMapping = handlerMapping;
        this.globalAllowedContentTypesLower = properties.getContentType().getAllowedTypes().stream()
                .map(String::toLowerCase)
                .collect(Collectors.toUnmodifiableList());
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
            validateContentType(request, resolveEffectiveContentTypes(handlerMethod, headerConstraints));
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

        // Step 6 — inline @HeaderRule entries from @HeaderConstraints
        if (headerConstraints != null) {
            for (HeaderRule rule : headerConstraints.value()) {
                String value = request.getHeader(rule.name());
                if (rule.required() && (value == null || value.isBlank())) {
                    String msg = rule.message().isBlank()
                            ? "Required header '" + rule.name() + "' is missing or blank"
                            : rule.message();
                    throw new HeaderValidationException(msg, HttpStatus.BAD_REQUEST, rule.name());
                }
                if (value != null && rule.notBlank() && value.isBlank()) {
                    String msg = rule.message().isBlank()
                            ? "Header '" + rule.name() + "' must not be blank when present"
                            : rule.message();
                    throw new HeaderValidationException(msg, HttpStatus.BAD_REQUEST, rule.name());
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isBypassPath(String path) {
        return bypassPathCache.computeIfAbsent(path,
                p -> properties.getBypassPaths().stream()
                        .anyMatch(pattern -> pathMatcher.match(pattern, p)));
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

    /** {@code allowedLower} must already be lowercased — call {@link #resolveEffectiveContentTypes} to obtain it. */
    private void validateContentType(HttpServletRequest request, List<String> allowedLower) {
        String contentType = request.getContentType();
        if (contentType == null || contentType.isBlank()) {
            throw new HeaderValidationException(
                    "Content-Type header is required for " + request.getMethod() + " requests",
                    HttpStatus.BAD_REQUEST,
                    "Content-Type");
        }
        // Lowercase once; prefix match allows "application/json; charset=UTF-8"
        String contentTypeLower = contentType.toLowerCase();
        boolean matched = allowedLower.stream().anyMatch(contentTypeLower::startsWith);
        if (!matched) {
            throw new HeaderValidationException(
                    "Content-Type '" + contentType + "' is not allowed. Allowed types: " + allowedLower,
                    HttpStatus.BAD_REQUEST,
                    "Content-Type");
        }
    }

    /**
     * Returns the effective (pre-lowercased) allowed content-type list for the request.
     * Per-endpoint annotation overrides are cached; falls back to the global list.
     */
    private List<String> resolveEffectiveContentTypes(HandlerMethod handlerMethod,
                                                      HeaderConstraints headerConstraints) {
        if (headerConstraints == null || headerConstraints.allowedContentTypes().length == 0
                || handlerMethod == null) {
            return globalAllowedContentTypesLower;
        }
        return effectiveContentTypesCache.computeIfAbsent(handlerMethod.getMethod(), m ->
            Arrays.stream(headerConstraints.allowedContentTypes())
                    .map(String::toLowerCase)
                    .collect(Collectors.toUnmodifiableList())
        );
    }

    /**
     * Returns the effective {@link HeaderConstraints} for the given handler method.
     * Method-level annotation takes full precedence over class-level. Result is cached per method.
     */
    private HeaderConstraints resolveHeaderConstraints(HandlerMethod handlerMethod) {
        if (handlerMethod == null) {
            return null;
        }
        return headerConstraintsCache.computeIfAbsent(handlerMethod.getMethod(), m -> {
            HeaderConstraints methodLevel = handlerMethod.getMethodAnnotation(HeaderConstraints.class);
            if (methodLevel != null) {
                return Optional.of(methodLevel);
            }
            return Optional.ofNullable(handlerMethod.getBeanType().getAnnotation(HeaderConstraints.class));
        }).orElse(null);
    }
}
