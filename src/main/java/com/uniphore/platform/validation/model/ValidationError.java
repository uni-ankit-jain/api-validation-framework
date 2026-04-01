package com.uniphore.platform.validation.model;

public class ValidationError {

    private final String field;
    private final String message;
    private final Object rejectedValue;

    public ValidationError(String field, String message, Object rejectedValue) {
        this.field = field;
        this.message = message;
        this.rejectedValue = rejectedValue;
    }

    public ValidationError(String field, String message) {
        this(field, message, null);
    }

    public String getField() {
        return field;
    }

    public String getMessage() {
        return message;
    }

    public Object getRejectedValue() {
        return rejectedValue;
    }
}
