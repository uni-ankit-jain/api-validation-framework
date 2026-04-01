package com.uniphore.platform.validation.exception;

import java.util.List;
import java.util.Map;

public class BodyValidationException extends RuntimeException {

    private final Map<String, List<String>> fieldErrors;

    public BodyValidationException(String message, Map<String, List<String>> fieldErrors) {
        super(message);
        this.fieldErrors = fieldErrors;
    }

    public Map<String, List<String>> getFieldErrors() {
        return fieldErrors;
    }
}
