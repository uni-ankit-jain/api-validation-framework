package com.uniphore.platform.validation.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validator implementation for {@link NotBlankIfPresent}.
 */
public class NotBlankIfPresentValidator implements ConstraintValidator<NotBlankIfPresent, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;  // absent is always OK
        }
        return !value.isBlank();
    }
}
