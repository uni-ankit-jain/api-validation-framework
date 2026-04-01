package com.uniphore.platform.validation.exception;

import org.springframework.http.HttpStatus;

public class HeaderValidationException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final String headerName;

    public HeaderValidationException(String message, HttpStatus httpStatus, String headerName) {
        super(message);
        this.httpStatus = httpStatus;
        this.headerName = headerName;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getHeaderName() {
        return headerName;
    }
}
