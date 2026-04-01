package com.uniphore.platform.validation.handler;

import com.uniphore.platform.validation.exception.BodyValidationException;
import com.uniphore.platform.validation.exception.HeaderValidationException;
import com.uniphore.platform.validation.model.ValidationErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationExceptionHandlerTest {

    private ValidationExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ValidationExceptionHandler();
    }

    // -----------------------------------------------------------------------
    // HeaderValidationException
    // -----------------------------------------------------------------------

    @Test
    void shouldReturn401ForMissingAuthorizationHeader() {
        HeaderValidationException ex = new HeaderValidationException(
                "Authorization header is required", HttpStatus.UNAUTHORIZED, "Authorization");

        ResponseEntity<ValidationErrorResponse> response = handler.handleHeaderValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(401);
        assertThat(response.getBody().getMessage()).isEqualTo("Authorization header is required");
        assertThat(response.getBody().getErrors()).hasSize(1);
        assertThat(response.getBody().getErrors().get(0).getField()).isEqualTo("Authorization");
    }

    @Test
    void shouldReturn400ForBadContentType() {
        HeaderValidationException ex = new HeaderValidationException(
                "Content-Type is not allowed", HttpStatus.BAD_REQUEST, "Content-Type");

        ResponseEntity<ValidationErrorResponse> response = handler.handleHeaderValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getStatus()).isEqualTo(400);
        assertThat(response.getBody().getErrors().get(0).getField()).isEqualTo("Content-Type");
    }

    @Test
    void shouldReturn422ForMissingPerEndpointHeader() {
        HeaderValidationException ex = new HeaderValidationException(
                "Required header 'X-Source' is missing", HttpStatus.UNPROCESSABLE_ENTITY, "X-Source");

        ResponseEntity<ValidationErrorResponse> response = handler.handleHeaderValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().getStatus()).isEqualTo(422);
    }

    @Test
    void shouldIncludeTimestampInResponse() {
        HeaderValidationException ex = new HeaderValidationException(
                "Test", HttpStatus.UNAUTHORIZED, "Authorization");

        ResponseEntity<ValidationErrorResponse> response = handler.handleHeaderValidation(ex);

        assertThat(response.getBody().getTimestamp()).isNotBlank();
    }

    // -----------------------------------------------------------------------
    // BodyValidationException
    // -----------------------------------------------------------------------

    @Test
    void shouldReturn400WithFieldErrorsForBodyValidationException() {
        Map<String, List<String>> fieldErrors = Map.of(
                "name", List.of("must not be blank"),
                "email", List.of("must be a valid email", "must not be null")
        );
        BodyValidationException ex = new BodyValidationException("Validation failed", fieldErrors);

        ResponseEntity<ValidationErrorResponse> response = handler.handleBodyValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getStatus()).isEqualTo(400);
        assertThat(response.getBody().getErrors()).hasSize(3);
    }

    // -----------------------------------------------------------------------
    // MethodArgumentNotValidException
    // -----------------------------------------------------------------------

    @Test
    void shouldReturn400WithFieldErrorsForMethodArgumentNotValidException() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
        bindingResult.addError(new FieldError("target", "name", "", true,
                null, null, "must not be blank"));
        bindingResult.addError(new FieldError("target", "email", "bad", true,
                null, null, "must be a valid email address"));

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<ValidationErrorResponse> response = handler.handleMethodArgumentNotValid(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getErrors()).hasSize(2);

        boolean hasNameError = response.getBody().getErrors().stream()
                .anyMatch(e -> "name".equals(e.getField()) && "must not be blank".equals(e.getMessage()));
        assertThat(hasNameError).isTrue();
    }

    @Test
    void shouldPopulateMessageFromFirstFieldErrorInMethodArgumentNotValid() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
        bindingResult.addError(new FieldError("target", "phone", null, true,
                null, null, "must not be null"));

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<ValidationErrorResponse> response = handler.handleMethodArgumentNotValid(ex);

        assertThat(response.getBody().getMessage()).isEqualTo("must not be null");
    }

    @Test
    void shouldReturnGenericMessageWhenNoFieldErrors() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<ValidationErrorResponse> response = handler.handleMethodArgumentNotValid(ex);

        assertThat(response.getBody().getMessage()).isEqualTo("Validation failed");
    }
}
