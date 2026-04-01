package com.uniphore.platform.validation.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "uniphore.validation")
public class ValidationProperties {

    private boolean enabled = true;
    private AuthorizationHeader authorizationHeader = new AuthorizationHeader();
    private ContentType contentType = new ContentType();
    private CustomHeaders customHeaders = new CustomHeaders();
    private List<String> bypassPaths = List.of("/health/**", "/swagger-ui/**", "/v3/api-docs/**");
    private int filterOrder = Integer.MIN_VALUE + 10;
    private ExceptionHandler exceptionHandler = new ExceptionHandler();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public AuthorizationHeader getAuthorizationHeader() {
        return authorizationHeader;
    }

    public void setAuthorizationHeader(AuthorizationHeader authorizationHeader) {
        this.authorizationHeader = authorizationHeader;
    }

    public ContentType getContentType() {
        return contentType;
    }

    public void setContentType(ContentType contentType) {
        this.contentType = contentType;
    }

    public CustomHeaders getCustomHeaders() {
        return customHeaders;
    }

    public void setCustomHeaders(CustomHeaders customHeaders) {
        this.customHeaders = customHeaders;
    }

    public List<String> getBypassPaths() {
        return bypassPaths;
    }

    public void setBypassPaths(List<String> bypassPaths) {
        this.bypassPaths = bypassPaths;
    }

    public int getFilterOrder() {
        return filterOrder;
    }

    public void setFilterOrder(int filterOrder) {
        this.filterOrder = filterOrder;
    }

    public ExceptionHandler getExceptionHandler() {
        return exceptionHandler;
    }

    public void setExceptionHandler(ExceptionHandler exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }

    public static class AuthorizationHeader {
        private boolean required = true;
        private boolean requireBearerPrefix = true;

        public boolean isRequired() {
            return required;
        }

        public void setRequired(boolean required) {
            this.required = required;
        }

        public boolean isRequireBearerPrefix() {
            return requireBearerPrefix;
        }

        public void setRequireBearerPrefix(boolean requireBearerPrefix) {
            this.requireBearerPrefix = requireBearerPrefix;
        }
    }

    public static class ContentType {
        private boolean validateOnMutating = true;
        private List<String> allowedTypes = List.of("application/json");

        public boolean isValidateOnMutating() {
            return validateOnMutating;
        }

        public void setValidateOnMutating(boolean validateOnMutating) {
            this.validateOnMutating = validateOnMutating;
        }

        public List<String> getAllowedTypes() {
            return allowedTypes;
        }

        public void setAllowedTypes(List<String> allowedTypes) {
            this.allowedTypes = allowedTypes;
        }
    }

    public static class CustomHeaders {
        private List<String> required = List.of();
        private List<String> notBlankIfPresent = List.of();

        public List<String> getRequired() {
            return required;
        }

        public void setRequired(List<String> required) {
            this.required = required;
        }

        public List<String> getNotBlankIfPresent() {
            return notBlankIfPresent;
        }

        public void setNotBlankIfPresent(List<String> notBlankIfPresent) {
            this.notBlankIfPresent = notBlankIfPresent;
        }
    }

    public static class ExceptionHandler {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
