package com.uniphore.platform.validation.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Validator implementation for {@link SafeString}.
 * Null values are considered valid (pair with {@code @NotNull} if null should be rejected).
 */
public class SafeStringValidator implements ConstraintValidator<SafeString, String> {

    private Pattern pattern;

    @Override
    public void initialize(SafeString annotation) {
        try {
            this.pattern = Pattern.compile(annotation.pattern());
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException(
                    "@SafeString pattern is not a valid regex: " + annotation.pattern(), e);
        }
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;  // use @NotNull separately if null should fail
        }
        return pattern.matcher(value).matches();
    }
}
