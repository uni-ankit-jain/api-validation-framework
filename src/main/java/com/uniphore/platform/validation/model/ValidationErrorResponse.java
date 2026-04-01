package com.uniphore.platform.validation.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ValidationErrorResponse {

    private final String timestamp;
    private final int status;
    private final String error;
    private final String message;
    private final String traceId;
    private final String tenantId;
    private final List<ValidationError> errors;

    private ValidationErrorResponse(Builder builder) {
        this.timestamp = builder.timestamp;
        this.status = builder.status;
        this.error = builder.error;
        this.message = builder.message;
        this.traceId = builder.traceId;
        this.tenantId = builder.tenantId;
        this.errors = builder.errors;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public int getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public String getMessage() {
        return message;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public List<ValidationError> getErrors() {
        return errors;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String timestamp = Instant.now().toString();
        private int status;
        private String error;
        private String message;
        private String traceId;
        private String tenantId;
        private List<ValidationError> errors;

        public Builder timestamp(String timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder status(int status) {
            this.status = status;
            return this;
        }

        public Builder error(String error) {
            this.error = error;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder errors(List<ValidationError> errors) {
            this.errors = errors;
            return this;
        }

        public ValidationErrorResponse build() {
            return new ValidationErrorResponse(this);
        }
    }
}
