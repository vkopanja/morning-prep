package com.usergems.morningprep.exception;

/**
 * Exception thrown when Person API operations fail.
 */
public class PersonApiException extends RuntimeException {

    public PersonApiException(String message) {
        super(message);
    }

    public PersonApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
